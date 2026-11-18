package com.hrms.modules.selfservice.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hrms.shared.common.ApiException;
import com.hrms.shared.common.PageResponse;
import com.hrms.modules.attendance.model.Attendance;
import com.hrms.modules.attendance.repository.AttendanceRepo;
import com.hrms.modules.attendance.service.AttendanceService;
import com.hrms.modules.employee.model.Employee;
import com.hrms.modules.employee.service.EmployeeService;
import com.hrms.modules.expense.model.ExpenseClaim;
import com.hrms.modules.expense.model.ExpenseType;
import com.hrms.modules.expense.repository.ExpenseTypeRepo;
import com.hrms.modules.expense.repository.ExpenseClaimRepo;
import com.hrms.modules.expense.service.ExpenseService;
import com.hrms.modules.leave.model.LeaveApplication;
import com.hrms.modules.leave.model.LeaveType;
import com.hrms.modules.leave.repository.LeaveTypeRepo;
import com.hrms.modules.leave.repository.LeaveApplicationRepo;
import com.hrms.modules.leave.service.LeaveService;
import com.hrms.modules.payroll.model.PayrollRun;
import com.hrms.modules.payroll.model.SalarySlip;
import com.hrms.modules.payroll.repository.SalarySlipRepo;
import com.hrms.modules.performance.model.Appraisal;
import com.hrms.modules.performance.model.Goal;
import com.hrms.modules.performance.repository.GoalRepo;
import com.hrms.modules.performance.repository.AppraisalRepo;
import com.hrms.shared.security.CurrentUser;

import lombok.RequiredArgsConstructor;

/** Self-service: every endpoint here works on the signed-in user's own employee record only. */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final EmployeeService employeeService;
    private final LeaveService leaveService;
    private final LeaveApplicationRepo leaveApplications;
    private final AttendanceService attendanceService;
    private final AttendanceRepo attendance;
    private final SalarySlipRepo slips;
    private final ExpenseService expenseService;
    private final ExpenseClaimRepo claims;
    private final GoalRepo goals;
    private final AppraisalRepo appraisals;
    private final LeaveTypeRepo leaveTypeRepo;
    private final ExpenseTypeRepo expenseTypeRepo;

    public record ProgressRequest(Integer progress) {
    }

    public record SelfRatingRequest(java.math.BigDecimal selfRating) {
    }

    @GetMapping("/profile")
    public Employee profile() {
        return employeeService.get(CurrentUser.employeeId());
    }

    // ---- lookups for the self-service forms (no module permission needed) ----

    @GetMapping("/leave-types")
    public List<LeaveType> leaveTypes() {
        return leaveTypeRepo.findAll();
    }

    @GetMapping("/expense-types")
    public List<ExpenseType> expenseTypes() {
        return expenseTypeRepo.findAll();
    }

    // ---- leave ----

    @GetMapping("/leaves")
    public Object leaves(@RequestParam(required = false) Integer page,
                         @RequestParam(defaultValue = "20") int size) {
        Long empId = CurrentUser.employeeId();
        if (page != null) {
            return PageResponse.of(leaveApplications.findByEmployeeIdOrderByFromDateDesc(
                    empId, PageResponse.pageable(page, size)));
        }
        return leaveApplications.findByEmployeeIdOrderByFromDateDesc(empId);
    }

    @PostMapping("/leaves")
    public LeaveApplication applyLeave(@RequestBody LeaveApplication body) {
        return leaveService.apply(CurrentUser.employeeId(), body);
    }

    @PostMapping("/leaves/{id}/cancel")
    public LeaveApplication cancelLeave(@PathVariable Long id) {
        return leaveService.cancel(id, CurrentUser.employeeId());
    }

    @GetMapping("/leave-balance")
    public List<LeaveService.Balance> leaveBalance() {
        return leaveService.balances(CurrentUser.employeeId(), LocalDate.now());
    }

    // ---- attendance ----

    @GetMapping("/attendance")
    public Object attendance(@RequestParam(required = false) Integer year,
                             @RequestParam(required = false) Integer month,
                             @RequestParam(required = false) Integer page,
                             @RequestParam(defaultValue = "20") int size) {
        LocalDate today = LocalDate.now();
        YearMonth ym = AttendanceService.reportPeriod(
                year == null ? today.getYear() : year, month == null ? today.getMonthValue() : month);
        Long empId = CurrentUser.employeeId();
        if (page != null) {
            return PageResponse.of(attendance.findByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDate(
                    empId, ym.atDay(1), ym.atEndOfMonth(), PageResponse.pageable(page, size)));
        }
        return attendance.findByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDate(
                empId, ym.atDay(1), ym.atEndOfMonth());
    }

    @GetMapping("/attendance/today")
    public Attendance today() {
        return attendance.findByEmployeeIdAndAttendanceDate(CurrentUser.employeeId(), LocalDate.now()).orElse(null);
    }

    @PostMapping("/check-in")
    public Attendance checkIn() {
        return attendanceService.checkIn(CurrentUser.employeeId());
    }

    @PostMapping("/check-out")
    public Attendance checkOut() {
        return attendanceService.checkOut(CurrentUser.employeeId());
    }

    // ---- payslips ----

    @GetMapping("/payslips")
    public Object payslips(@RequestParam(required = false) Integer page,
                           @RequestParam(defaultValue = "20") int size) {
        Long empId = CurrentUser.employeeId();
        if (page != null) {
            return PageResponse.of(slips.findByEmployeeIdAndStatusOrderByPeriodYearDescPeriodMonthDesc(
                    empId, PayrollRun.Status.SUBMITTED, PageResponse.pageable(page, size)));
        }
        return slips.findByEmployeeIdAndStatusOrderByPeriodYearDescPeriodMonthDesc(
                empId, PayrollRun.Status.SUBMITTED);
    }

    // ---- expenses ----

    @GetMapping("/expenses")
    public Object expenses(@RequestParam(required = false) Integer page,
                           @RequestParam(defaultValue = "20") int size) {
        Long empId = CurrentUser.employeeId();
        if (page != null) {
            return PageResponse.of(claims.findByEmployeeIdOrderByClaimDateDesc(
                    empId, PageResponse.pageable(page, size)));
        }
        return claims.findByEmployeeIdOrderByClaimDateDesc(empId);
    }

    @PostMapping("/expenses")
    public ExpenseClaim submitExpense(@RequestBody ExpenseClaim body) {
        return expenseService.submit(CurrentUser.employeeId(), body);
    }

    // ---- performance ----

    @GetMapping("/goals")
    public Object goals(@RequestParam(required = false) Integer page,
                        @RequestParam(defaultValue = "20") int size) {
        Long empId = CurrentUser.employeeId();
        if (page != null) {
            return PageResponse.of(goals.findByEmployeeIdOrderByIdDesc(
                    empId, PageResponse.pageable(page, size)));
        }
        return goals.findByEmployeeIdOrderByIdDesc(empId);
    }

    @PostMapping("/goals/{id}/progress")
    public Goal updateProgress(@PathVariable Long id, @RequestBody ProgressRequest req) {
        Goal goal = goals.findById(id).orElseThrow(() -> ApiException.notFound("Goal not found"));
        if (!goal.getEmployee().getId().equals(CurrentUser.employeeId())) {
            throw ApiException.forbidden("This is not your goal");
        }
        if (req.progress() == null || req.progress() < 0 || req.progress() > 100) {
            throw ApiException.badRequest("Progress must be between 0 and 100");
        }
        goal.setProgress(req.progress());
        if (req.progress() == 100) {
            goal.setStatus(Goal.Status.COMPLETED);
        } else if (req.progress() > 0 && goal.getStatus() == Goal.Status.OPEN) {
            goal.setStatus(Goal.Status.IN_PROGRESS);
        }
        return goals.save(goal);
    }

    @GetMapping("/appraisals")
    public Object appraisals(@RequestParam(required = false) Integer page,
                             @RequestParam(defaultValue = "20") int size) {
        Long empId = CurrentUser.employeeId();
        if (page != null) {
            return PageResponse.of(appraisals.findByEmployeeIdOrderByIdDesc(
                    empId, PageResponse.pageable(page, size)));
        }
        return appraisals.findByEmployeeIdOrderByIdDesc(empId);
    }

    @PostMapping("/appraisals/{id}/self-rating")
    public Appraisal selfRate(@PathVariable Long id, @RequestBody SelfRatingRequest req) {
        Appraisal a = appraisals.findById(id).orElseThrow(() -> ApiException.notFound("Appraisal not found"));
        if (!a.getEmployee().getId().equals(CurrentUser.employeeId())) {
            throw ApiException.forbidden("This is not your appraisal");
        }
        if (a.getStatus() == Appraisal.Status.FINALIZED) {
            throw ApiException.badRequest("This appraisal is already finalized");
        }
        if (req.selfRating() == null || req.selfRating().compareTo(java.math.BigDecimal.ONE) < 0
                || req.selfRating().compareTo(java.math.BigDecimal.valueOf(5)) > 0) {
            throw ApiException.badRequest("Rating must be between 1 and 5");
        }
        a.setSelfRating(req.selfRating());
        if (a.getStatus() == Appraisal.Status.DRAFT) {
            a.setStatus(Appraisal.Status.SELF_REVIEWED);
        }
        return appraisals.save(a);
    }
}
