package com.company.hrms.selfservice.controller;

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

import com.company.hrms.common.ApiException;
import com.company.hrms.attendance.model.Attendance;
import com.company.hrms.attendance.repository.ShiftTypeRepo;
import com.company.hrms.attendance.repository.AttendanceRepo;
import com.company.hrms.attendance.service.AttendanceService;
import com.company.hrms.employee.model.Employee;
import com.company.hrms.employee.service.EmployeeService;
import com.company.hrms.expense.model.ExpenseClaim;
import com.company.hrms.expense.model.ExpenseType;
import com.company.hrms.expense.repository.ExpenseTypeRepo;
import com.company.hrms.expense.repository.ExpenseClaimRepo;
import com.company.hrms.expense.repository.EmployeeAdvanceRepo;
import com.company.hrms.expense.service.ExpenseService;
import com.company.hrms.leave.model.LeaveApplication;
import com.company.hrms.leave.model.LeaveType;
import com.company.hrms.leave.repository.HolidayListRepo;
import com.company.hrms.leave.repository.LeaveTypeRepo;
import com.company.hrms.leave.repository.LeaveAllocationRepo;
import com.company.hrms.leave.repository.LeaveApplicationRepo;
import com.company.hrms.leave.service.LeaveService;
import com.company.hrms.payroll.model.PayrollRun;
import com.company.hrms.payroll.model.SalarySlip;
import com.company.hrms.payroll.repository.SalaryComponentRepo;
import com.company.hrms.payroll.repository.SalaryStructureRepo;
import com.company.hrms.payroll.repository.SalaryAssignmentRepo;
import com.company.hrms.payroll.repository.PayrollRunRepo;
import com.company.hrms.payroll.repository.SalarySlipRepo;
import com.company.hrms.performance.model.Appraisal;
import com.company.hrms.performance.model.Goal;
import com.company.hrms.performance.repository.AppraisalCycleRepo;
import com.company.hrms.performance.repository.GoalRepo;
import com.company.hrms.performance.repository.AppraisalRepo;
import com.company.hrms.security.CurrentUser;

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
    public List<LeaveApplication> leaves() {
        return leaveApplications.findByEmployeeIdOrderByFromDateDesc(CurrentUser.employeeId());
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
    public List<Attendance> attendance(@RequestParam(required = false) Integer year,
                                       @RequestParam(required = false) Integer month) {
        LocalDate today = LocalDate.now();
        YearMonth ym = YearMonth.of(year == null ? today.getYear() : year, month == null ? today.getMonthValue() : month);
        return attendance.findByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDate(
                CurrentUser.employeeId(), ym.atDay(1), ym.atEndOfMonth());
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
    public List<SalarySlip> payslips() {
        return slips.findByEmployeeIdAndStatusOrderByPeriodYearDescPeriodMonthDesc(
                CurrentUser.employeeId(), PayrollRun.Status.SUBMITTED);
    }

    // ---- expenses ----

    @GetMapping("/expenses")
    public List<ExpenseClaim> expenses() {
        return claims.findByEmployeeIdOrderByClaimDateDesc(CurrentUser.employeeId());
    }

    @PostMapping("/expenses")
    public ExpenseClaim submitExpense(@RequestBody ExpenseClaim body) {
        return expenseService.submit(CurrentUser.employeeId(), body);
    }

    // ---- performance ----

    @GetMapping("/goals")
    public List<Goal> goals() {
        return goals.findByEmployeeIdOrderByIdDesc(CurrentUser.employeeId());
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
    public List<Appraisal> appraisals() {
        return appraisals.findByEmployeeIdOrderByIdDesc(CurrentUser.employeeId());
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
