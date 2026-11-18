package com.hrms.modules.leave.service;

import com.hrms.modules.leave.model.Holiday;
import com.hrms.modules.leave.model.HolidayList;
import com.hrms.modules.leave.model.LeaveAllocation;
import com.hrms.modules.leave.model.LeaveApplication;
import com.hrms.modules.leave.model.LeaveType;
import com.hrms.modules.leave.repository.HolidayListRepo;
import com.hrms.modules.leave.repository.LeaveAllocationRepo;
import com.hrms.modules.leave.repository.LeaveApplicationRepo;
import com.hrms.modules.leave.repository.LeaveTypeRepo;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hrms.shared.common.ApprovalStatus;
import com.hrms.shared.common.ApiException;
import com.hrms.modules.attendance.model.Attendance;
import com.hrms.modules.attendance.repository.AttendanceRepo;
import com.hrms.modules.employee.model.Employee;
import com.hrms.modules.employee.service.EmployeeService;
import com.hrms.modules.notification.service.NotificationService;
import com.hrms.shared.security.CurrentUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LeaveService {

    /** Statuses that use up leave balance. */
    private static final List<ApprovalStatus> COUNTED = List.of(ApprovalStatus.PENDING, ApprovalStatus.APPROVED);
    private static final String LEAVE_REMARK_PREFIX = "Leave: ";

    private final HolidayListRepo holidayLists;
    private final LeaveTypeRepo leaveTypes;
    private final LeaveAllocationRepo allocations;
    private final LeaveApplicationRepo applications;
    private final AttendanceRepo attendance;
    private final EmployeeService employeeService;
    private final NotificationService notifications;

    public record Balance(Long leaveTypeId, String leaveType, boolean unpaid, BigDecimal allocated, BigDecimal used,
                          BigDecimal balance) {
    }

    /** Weekly offs and listed holidays for this employee inside the range (both ends included). */
    @Transactional(readOnly = true)
    public Set<LocalDate> holidaysBetween(Employee employee, LocalDate from, LocalDate to) {
        HolidayList list = employee.getHolidayList() != null
                ? holidayLists.findById(employee.getHolidayList().getId()).orElse(null)
                : holidayLists.findFirstByDefaultListTrue().orElse(null);
        Set<LocalDate> out = new HashSet<>();
        if (list == null) {
            return out;
        }
        Set<DayOfWeek> offs = EnumSet.noneOf(DayOfWeek.class);
        if (list.getWeeklyOffs() != null) {
            for (String s : list.getWeeklyOffs().split(",")) {
                try {
                    offs.add(DayOfWeek.valueOf(s.trim().toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                    // unknown weekday name: skip it
                }
            }
        }
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (offs.contains(d.getDayOfWeek())) {
                out.add(d);
            }
        }
        for (Holiday h : list.getHolidays()) {
            LocalDate d = h.getHolidayDate();
            if (d != null && !d.isBefore(from) && !d.isAfter(to)) {
                out.add(d);
            }
        }
        return out;
    }

    public BigDecimal countLeaveDays(Employee employee, LocalDate from, LocalDate to, boolean halfDay) {
        Set<LocalDate> holidays = holidaysBetween(employee, from, to);
        int days = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (!holidays.contains(d)) {
                days++;
            }
        }
        if (halfDay && days == 1) {
            return new BigDecimal("0.5");
        }
        return BigDecimal.valueOf(days);
    }

    @Transactional(readOnly = true)
    public List<Balance> balances(Long employeeId, LocalDate onDate) {
        List<Balance> out = new ArrayList<>();
        for (LeaveType type : leaveTypes.findAll()) {
            out.add(balance(employeeId, type, onDate));
        }
        return out;
    }

    private Balance balance(Long employeeId, LeaveType type, LocalDate onDate) {
        List<LeaveAllocation> current = allocations.findByEmployeeId(employeeId).stream()
                .filter(a -> a.getLeaveType().getId().equals(type.getId()))
                .filter(a -> !onDate.isBefore(a.getFromDate()) && !onDate.isAfter(a.getToDate()))
                .toList();
        BigDecimal allocated = current.stream().map(LeaveAllocation::getDays).reduce(BigDecimal.ZERO, BigDecimal::add);
        // Leave counts against the allocation period it starts in; unpaid leave counts for the calendar year.
        LocalDate start = current.stream().map(LeaveAllocation::getFromDate).min(LocalDate::compareTo)
                .orElse(onDate.withDayOfYear(1));
        LocalDate end = current.stream().map(LeaveAllocation::getToDate).max(LocalDate::compareTo)
                .orElse(onDate.withDayOfYear(onDate.lengthOfYear()));
        BigDecimal used = applications.findByEmployeeIdAndStatusIn(employeeId, COUNTED).stream()
                .filter(a -> a.getLeaveType().getId().equals(type.getId()))
                .filter(a -> !a.getFromDate().isBefore(start) && !a.getFromDate().isAfter(end))
                .map(LeaveApplication::getTotalDays)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Balance(type.getId(), type.getName(), type.isUnpaid(), allocated, used, allocated.subtract(used));
    }

    @Transactional
    public LeaveApplication apply(Long employeeId, LeaveApplication req) {
        Employee employee = employeeService.get(employeeId);
        if (req.getLeaveType() == null || req.getLeaveType().getId() == null) {
            throw ApiException.badRequest("Choose a leave type");
        }
        LeaveType type = leaveTypes.findById(req.getLeaveType().getId())
                .orElseThrow(() -> ApiException.badRequest("Leave type not found"));
        LocalDate from = req.getFromDate();
        LocalDate to = req.getToDate();
        if (from == null || to == null) {
            throw ApiException.badRequest("From date and to date are required");
        }
        if (to.isBefore(from)) {
            throw ApiException.badRequest("To date cannot be before from date");
        }
        if (req.isHalfDay() && !from.equals(to)) {
            throw ApiException.badRequest("Half day applies to a single date only");
        }
        BigDecimal days = countLeaveDays(employee, from, to, req.isHalfDay());
        if (days.signum() == 0) {
            throw ApiException.badRequest("The selected dates are all holidays");
        }
        boolean overlaps = applications.findByEmployeeIdAndStatusIn(employeeId, COUNTED).stream()
                .anyMatch(a -> !a.getFromDate().isAfter(to) && !a.getToDate().isBefore(from));
        if (overlaps) {
            throw ApiException.badRequest("Another leave application already covers these dates");
        }
        if (!type.isUnpaid() && !type.isAllowNegative()) {
            Balance b = balance(employeeId, type, from);
            if (b.balance().compareTo(days) < 0) {
                throw ApiException.badRequest("Only " + b.balance().stripTrailingZeros().toPlainString()
                        + " day(s) of " + type.getName() + " left; this request needs "
                        + days.stripTrailingZeros().toPlainString());
            }
        }

        LeaveApplication app = new LeaveApplication();
        app.setEmployee(employee);
        app.setLeaveType(type);
        app.setFromDate(from);
        app.setToDate(to);
        app.setHalfDay(req.isHalfDay());
        app.setTotalDays(days);
        app.setReason(req.getReason());
        app.setStatus(ApprovalStatus.PENDING);
        app = applications.save(app);

        notifications.notifyPermission("leave:approve", "Leave request from " + employee.getFullName(),
                employee.getFullName() + " requested " + type.getName() + " from " + from + " to " + to + " ("
                        + days.stripTrailingZeros().toPlainString() + " day(s)).");
        return app;
    }

    @Transactional
    public LeaveApplication decide(Long id, boolean approve, String remarks) {
        LeaveApplication app = get(id);
        if (app.getStatus() != ApprovalStatus.PENDING) {
            throw ApiException.badRequest("Only pending applications can be approved or rejected");
        }
        app.setStatus(approve ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        app.setDecidedBy(CurrentUser.usernameOrSystem());
        app.setDecidedAt(LocalDateTime.now());
        app.setRemarks(remarks);
        if (approve) {
            markAttendance(app);
        }
        app = applications.save(app);
        notifications.notifyEmployee(app.getEmployee(),
                "Leave " + (approve ? "approved" : "rejected"),
                "Your " + app.getLeaveType().getName() + " from " + app.getFromDate() + " to " + app.getToDate()
                        + " was " + (approve ? "approved" : "rejected") + "."
                        + (remarks == null || remarks.isBlank() ? "" : " Remarks: " + remarks));
        return app;
    }

    /** @param requesterEmployeeId when set, the application must belong to this employee */
    @Transactional
    public LeaveApplication cancel(Long id, Long requesterEmployeeId) {
        LeaveApplication app = get(id);
        if (requesterEmployeeId != null && !app.getEmployee().getId().equals(requesterEmployeeId)) {
            throw ApiException.forbidden("This is not your leave application");
        }
        if (app.getStatus() != ApprovalStatus.PENDING && app.getStatus() != ApprovalStatus.APPROVED) {
            throw ApiException.badRequest("This application is already " + app.getStatus().name().toLowerCase());
        }
        if (app.getStatus() == ApprovalStatus.APPROVED) {
            List<Attendance> rows = attendance.findByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDate(
                    app.getEmployee().getId(), app.getFromDate(), app.getToDate());
            attendance.deleteAll(rows.stream()
                    .filter(r -> leaveRemark(app).equals(r.getRemarks()))
                    .filter(r -> r.getStatus() == leaveAttendanceStatus(app))
                    .filter(r -> r.getInTime() == null && r.getOutTime() == null && r.getWorkingHours() == null)
                    .toList());
        }
        app.setStatus(ApprovalStatus.CANCELLED);
        return applications.save(app);
    }

    public LeaveApplication get(Long id) {
        return applications.findById(id).orElseThrow(() -> ApiException.notFound("Leave application not found"));
    }

    private void markAttendance(LeaveApplication app) {
        Employee employee = app.getEmployee();
        Set<LocalDate> holidays = holidaysBetween(employee, app.getFromDate(), app.getToDate());
        for (LocalDate d = app.getFromDate(); !d.isAfter(app.getToDate()); d = d.plusDays(1)) {
            if (holidays.contains(d)) {
                continue;
            }
            if (attendance.findByEmployeeIdAndAttendanceDate(employee.getId(), d).isPresent()) {
                continue;
            }
            Attendance row = new Attendance();
            row.setEmployee(employee);
            row.setAttendanceDate(d);
            row.setStatus(leaveAttendanceStatus(app));
            row.setRemarks(leaveRemark(app));
            attendance.save(row);
        }
    }

    private static Attendance.Status leaveAttendanceStatus(LeaveApplication app) {
        return app.isHalfDay() ? Attendance.Status.HALF_DAY : Attendance.Status.ON_LEAVE;
    }

    private static String leaveRemark(LeaveApplication app) {
        return LEAVE_REMARK_PREFIX + app.getLeaveType().getName();
    }
}
