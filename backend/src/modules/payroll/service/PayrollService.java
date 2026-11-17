package modules.payroll.service;

import modules.payroll.model.PayrollRun;
import modules.payroll.model.SalaryAssignment;
import modules.payroll.model.SalaryComponent;
import modules.payroll.model.SalarySlip;
import modules.payroll.model.SalarySlipLine;
import modules.payroll.model.SalaryStructure;
import modules.payroll.model.SalaryStructureLine;
import modules.payroll.repository.PayrollRunRepo;
import modules.payroll.repository.SalaryAssignmentRepo;
import modules.payroll.repository.SalarySlipRepo;
import modules.payroll.repository.SalaryStructureRepo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import shared.common.ApprovalStatus;
import shared.common.ApiException;
import modules.attendance.model.Attendance;
import modules.attendance.repository.AttendanceRepo;
import modules.employee.model.Employee;
import modules.employee.repository.EmployeeRepo;
import modules.leave.model.LeaveApplication;
import modules.leave.repository.LeaveApplicationRepo;
import modules.leave.service.LeaveService;
import modules.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PayrollService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PayrollRunRepo runs;
    private final SalarySlipRepo slips;
    private final SalaryAssignmentRepo assignments;
    private final SalaryStructureRepo structures;
    private final EmployeeRepo employees;
    private final AttendanceRepo attendance;
    private final LeaveApplicationRepo leaveApplications;
    private final LeaveService leaveService;
    private final NotificationService notifications;

    /** Calculates (or recalculates) draft salary slips for every eligible employee in the month. */
    @Transactional
    public PayrollRun run(int year, int month) {
        if (month < 1 || month > 12) {
            throw ApiException.badRequest("Month must be between 1 and 12");
        }
        YearMonth ym = YearMonth.of(year, month);
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        PayrollRun run = runs.findByPeriodYearAndPeriodMonth(year, month).orElseGet(() -> {
            PayrollRun r = new PayrollRun();
            r.setPeriodYear(year);
            r.setPeriodMonth(month);
            r.setStatus(PayrollRun.Status.DRAFT);
            return runs.save(r);
        });
        if (run.getStatus() == PayrollRun.Status.SUBMITTED) {
            throw ApiException.badRequest("Payroll for this month is already submitted");
        }
        slips.deleteAll(slips.findByRunIdOrderByEmployeeEmployeeCode(run.getId()));
        slips.flush();

        List<LeaveApplication> approvedLeaves = leaveApplications
                .findByStatusAndFromDateLessThanEqualAndToDateGreaterThanEqual(ApprovalStatus.APPROVED, monthEnd, monthStart);

        List<String> skipped = new ArrayList<>();
        BigDecimal totalNet = BigDecimal.ZERO;
        int count = 0;

        for (Employee e : employees.findAll()) {
            if (e.getDateOfJoining() == null || e.getDateOfJoining().isAfter(monthEnd)) {
                continue;
            }
            if (e.getRelievingDate() == null ? e.getStatus() != Employee.Status.ACTIVE
                    : e.getRelievingDate().isBefore(monthStart)) {
                continue;
            }
            SalaryAssignment assignment = assignments.findByEmployeeIdOrderByFromDateDesc(e.getId()).stream()
                    .filter(a -> !a.getFromDate().isAfter(monthEnd))
                    .findFirst().orElse(null);
            if (assignment == null) {
                skipped.add(e.getEmployeeCode() + " " + e.getFullName() + ": no salary assignment");
                continue;
            }
            SalaryStructure structure = structures.findById(assignment.getStructure().getId()).orElseThrow();

            // Days the employee was employed inside this month.
            LocalDate from = e.getDateOfJoining().isAfter(monthStart) ? e.getDateOfJoining() : monthStart;
            LocalDate to = e.getRelievingDate() != null && e.getRelievingDate().isBefore(monthEnd)
                    ? e.getRelievingDate() : monthEnd;

            Set<LocalDate> monthHolidays = leaveService.holidaysBetween(e, monthStart, monthEnd);
            int workingDays = 0;
            int employedDays = 0;
            for (LocalDate d = monthStart; !d.isAfter(monthEnd); d = d.plusDays(1)) {
                if (monthHolidays.contains(d)) {
                    continue;
                }
                workingDays++;
                if (!d.isBefore(from) && !d.isAfter(to)) {
                    employedDays++;
                }
            }

            BigDecimal lwp = BigDecimal.ZERO;
            for (Attendance a : attendance.findByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDate(e.getId(), from, to)) {
                if (a.getStatus() == Attendance.Status.ABSENT && !monthHolidays.contains(a.getAttendanceDate())) {
                    lwp = lwp.add(BigDecimal.ONE);
                }
            }
            for (LeaveApplication la : approvedLeaves) {
                if (!la.getEmployee().getId().equals(e.getId()) || !la.getLeaveType().isUnpaid()) {
                    continue;
                }
                LocalDate s = la.getFromDate().isBefore(from) ? from : la.getFromDate();
                LocalDate en = la.getToDate().isAfter(to) ? to : la.getToDate();
                for (LocalDate d = s; !d.isAfter(en); d = d.plusDays(1)) {
                    if (!monthHolidays.contains(d)) {
                        lwp = lwp.add(la.isHalfDay() ? new BigDecimal("0.5") : BigDecimal.ONE);
                    }
                }
            }

            BigDecimal working = BigDecimal.valueOf(workingDays);
            BigDecimal payment = BigDecimal.valueOf(employedDays).subtract(lwp).max(BigDecimal.ZERO);
            BigDecimal ratio = workingDays == 0 ? BigDecimal.ZERO : payment.divide(working, 6, RoundingMode.HALF_UP);
            BigDecimal base = assignment.getBaseAmount();

            SalarySlip slip = new SalarySlip();
            slip.setRun(run);
            slip.setEmployee(e);
            slip.setPeriodYear(year);
            slip.setPeriodMonth(month);
            slip.setWorkingDays(working);
            slip.setPaymentDays(payment);
            slip.setLwpDays(lwp);
            slip.setStatus(PayrollRun.Status.DRAFT);

            BigDecimal gross = BigDecimal.ZERO;
            for (SalaryStructureLine line : structure.getLines()) {
                SalaryComponent c = line.getComponent();
                if (c.getType() != SalaryComponent.Type.EARNING) {
                    continue;
                }
                BigDecimal amount = line.getAmountType() == SalaryStructureLine.AmountType.FIXED
                        ? line.getAmountValue()
                        : base.multiply(line.getAmountValue()).divide(HUNDRED, 6, RoundingMode.HALF_UP);
                if (c.isDependsOnPaymentDays()) {
                    amount = amount.multiply(ratio);
                }
                amount = amount.setScale(2, RoundingMode.HALF_UP);
                gross = gross.add(amount);
                slip.getLines().add(SalarySlipLine.of(c.getName(), c.getType(), amount));
            }

            BigDecimal deductions = BigDecimal.ZERO;
            for (SalaryStructureLine line : structure.getLines()) {
                SalaryComponent c = line.getComponent();
                if (c.getType() != SalaryComponent.Type.DEDUCTION) {
                    continue;
                }
                BigDecimal amount = switch (line.getAmountType()) {
                    case FIXED -> c.isDependsOnPaymentDays() ? line.getAmountValue().multiply(ratio) : line.getAmountValue();
                    case PERCENT_OF_BASE -> {
                        BigDecimal v = base.multiply(line.getAmountValue()).divide(HUNDRED, 6, RoundingMode.HALF_UP);
                        yield c.isDependsOnPaymentDays() ? v.multiply(ratio) : v;
                    }
                    case PERCENT_OF_GROSS -> gross.multiply(line.getAmountValue()).divide(HUNDRED, 6, RoundingMode.HALF_UP);
                };
                amount = amount.setScale(2, RoundingMode.HALF_UP);
                deductions = deductions.add(amount);
                slip.getLines().add(SalarySlipLine.of(c.getName(), c.getType(), amount));
            }

            slip.setGrossPay(gross);
            slip.setTotalDeduction(deductions);
            slip.setNetPay(gross.subtract(deductions));
            slips.save(slip);
            totalNet = totalNet.add(slip.getNetPay());
            count++;
        }

        run.setSlipCount(count);
        run.setTotalNet(totalNet);
        run = runs.save(run);
        run.setSkipped(skipped);
        return run;
    }

    /** Locks the run and tells each employee that the payslip is ready. */
    @Transactional
    public PayrollRun submit(Long runId) {
        PayrollRun run = runs.findById(runId).orElseThrow(() -> ApiException.notFound("Payroll run not found"));
        if (run.getStatus() == PayrollRun.Status.SUBMITTED) {
            throw ApiException.badRequest("This payroll run is already submitted");
        }
        List<SalarySlip> runSlips = slips.findByRunIdOrderByEmployeeEmployeeCode(runId);
        if (runSlips.isEmpty()) {
            throw ApiException.badRequest("There are no salary slips to submit");
        }
        String period = YearMonth.of(run.getPeriodYear(), run.getPeriodMonth()).getMonth()
                .getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + run.getPeriodYear();
        for (SalarySlip slip : runSlips) {
            slip.setStatus(PayrollRun.Status.SUBMITTED);
            slips.save(slip);
            notifications.notifyEmployee(slip.getEmployee(), "Payslip for " + period,
                    "Your payslip for " + period + " is ready. Net pay: " + slip.getNetPay().toPlainString() + ".");
        }
        run.setStatus(PayrollRun.Status.SUBMITTED);
        return runs.save(run);
    }

    @Transactional
    public void deleteRun(Long runId) {
        PayrollRun run = runs.findById(runId).orElseThrow(() -> ApiException.notFound("Payroll run not found"));
        if (run.getStatus() == PayrollRun.Status.SUBMITTED) {
            throw ApiException.badRequest("A submitted payroll run cannot be deleted");
        }
        slips.deleteAll(slips.findByRunIdOrderByEmployeeEmployeeCode(runId));
        runs.delete(run);
    }
}
