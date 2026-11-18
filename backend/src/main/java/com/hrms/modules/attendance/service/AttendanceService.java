package com.hrms.modules.attendance.service;

import com.hrms.modules.attendance.model.Attendance;
import com.hrms.modules.attendance.repository.AttendanceRepo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hrms.shared.common.ApiException;
import com.hrms.modules.employee.model.Employee;
import com.hrms.modules.employee.repository.EmployeeRepo;
import com.hrms.modules.employee.service.EmployeeService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    /** A checked-out day shorter than this counts as a half day. */
    private static final BigDecimal HALF_DAY_HOURS = new BigDecimal("4.5");

    private final AttendanceRepo attendance;
    private final EmployeeRepo employees;
    private final EmployeeService employeeService;

    /** Creates the record for this employee and date, or replaces the one already there. */
    @Transactional
    public Attendance mark(Attendance body) {
        return saveMark(null, body);
    }

    @Transactional
    public Attendance updateMark(Attendance existing, Attendance body) {
        return saveMark(existing, body);
    }

    private Attendance saveMark(Attendance existing, Attendance body) {
        if (body.getEmployee() == null || body.getEmployee().getId() == null) {
            throw ApiException.badRequest("Choose an employee");
        }
        if (body.getAttendanceDate() == null || body.getStatus() == null) {
            throw ApiException.badRequest("Date and status are required");
        }
        Employee employee = employeeService.get(body.getEmployee().getId());
        Attendance row = existing == null
                ? attendance.findByEmployeeIdAndAttendanceDate(employee.getId(), body.getAttendanceDate())
                        .orElseGet(Attendance::new)
                : existing;
        row.setEmployee(employee);
        row.setAttendanceDate(body.getAttendanceDate());
        row.setStatus(body.getStatus());
        row.setInTime(body.getInTime());
        row.setOutTime(body.getOutTime());
        row.setShift(body.getShift());
        row.setRemarks(body.getRemarks());
        fillHours(row);
        return attendance.save(row);
    }

    @Transactional
    public Attendance checkIn(Long employeeId) {
        Employee e = employeeService.get(employeeId);
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
        Attendance row = attendance.findByEmployeeIdAndAttendanceDate(employeeId, today)
                .orElseGet(() -> {
                    Attendance a = new Attendance();
                    a.setEmployee(e);
                    a.setAttendanceDate(today);
                    a.setStatus(Attendance.Status.PRESENT);
                    return a;
                });
        if (row.getInTime() == null) {
            row.setInTime(now);
        }
        fillHours(row);
        return attendance.save(row);
    }

    @Transactional
    public Attendance checkOut(Long employeeId) {
        LocalDate today = LocalDate.now();
        Attendance row = attendance.findByEmployeeIdAndAttendanceDate(employeeId, today)
                .orElseThrow(() -> ApiException.badRequest("You have not checked in today"));
        if (row.getInTime() == null) {
            throw ApiException.badRequest("You have not checked in today");
        }
        row.setOutTime(LocalTime.now().truncatedTo(ChronoUnit.MINUTES));
        fillHours(row);
        return attendance.save(row);
    }

    private void fillHours(Attendance a) {
        if (a.getInTime() == null || a.getOutTime() == null) {
            a.setWorkingHours(null);
            return;
        }
        long minutes = Duration.between(a.getInTime(), a.getOutTime()).toMinutes();
        if (minutes < 0) {
            minutes += 24 * 60; // crossed midnight
        }
        BigDecimal hours = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        a.setWorkingHours(hours);
        if (a.getStatus() == Attendance.Status.PRESENT && hours.compareTo(HALF_DAY_HOURS) < 0) {
            a.setStatus(Attendance.Status.HALF_DAY);
        }
    }

    /**
     * Matrix of all active employees x every day of the month.
     * Each cell is a status (PRESENT, ABSENT, ...), "OFF" for a weekend/holiday, or null for an unmarked day.
     */
    public Map<String, Object> monthlySummary(int year, int month) {
        YearMonth ym = reportPeriod(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        List<Employee> active = employees.findByStatus(Employee.Status.ACTIVE);
        Map<Long, Map<Integer, String>> byEmployee = new LinkedHashMap<>();
        for (Employee e : active) {
            byEmployee.put(e.getId(), new LinkedHashMap<>());
        }

        for (Attendance a : attendance.findByAttendanceDateBetweenOrderByAttendanceDateDesc(start, end)) {
            Map<Integer, String> days = byEmployee.get(a.getEmployee().getId());
            if (days != null) {
                days.put(a.getAttendanceDate().getDayOfMonth(), a.getStatus().name());
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Employee e : active) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("employeeId", e.getId());
            r.put("employeeCode", e.getEmployeeCode());
            r.put("employeeName", e.getFullName());
            r.put("department", e.getDepartment() == null ? "" : e.getDepartment().getName());
            Map<Integer, String> days = byEmployee.get(e.getId());
            long present = days.values().stream().filter("PRESENT"::equals).count();
            long half = days.values().stream().filter("HALF_DAY"::equals).count();
            long absent = days.values().stream().filter("ABSENT"::equals).count();
            long leave = days.values().stream().filter("ON_LEAVE"::equals).count();
            r.put("present", present);
            r.put("halfDay", half);
            r.put("absent", absent);
            r.put("onLeave", leave);
            r.put("days", days);
            rows.add(r);
        }

        return Map.of(
                "year", year,
                "month", month,
                "daysInMonth", ym.lengthOfMonth(),
                "employees", rows);
    }

    public static YearMonth reportPeriod(int year, int month) {
        if (year < 1 || year > 9999 || month < 1 || month > 12) {
            throw ApiException.badRequest("Provide a year from 1 to 9999 and a month from 1 to 12");
        }
        return YearMonth.of(year, month);
    }
}
