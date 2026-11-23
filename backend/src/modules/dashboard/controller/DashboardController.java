package modules.dashboard.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import shared.common.ApprovalStatus;
import modules.attendance.model.Attendance;
import modules.attendance.repository.AttendanceRepo;
import modules.employee.model.Employee;
import modules.employee.repository.EmployeeRepo;
import modules.expense.repository.ExpenseClaimRepo;
import modules.leave.model.LeaveApplication;
import modules.leave.repository.LeaveApplicationRepo;
import modules.payroll.model.PayrollRun;
import modules.payroll.repository.PayrollRunRepo;
import modules.recruitment.model.JobApplicant;
import modules.recruitment.model.JobOpening;
import modules.recruitment.repository.JobOpeningRepo;
import modules.recruitment.repository.JobApplicantRepo;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import shared.security.CurrentUser;
import shared.security.FinanceControls;

/** Numbers and chart series for the home screen. */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private static final int MONTHS = 12;

    private final EmployeeRepo employees;
    private final LeaveApplicationRepo leaveApplications;
    private final AttendanceRepo attendance;
    private final ExpenseClaimRepo claims;
    private final PayrollRunRepo payrollRuns;
    private final JobOpeningRepo openings;
    private final JobApplicantRepo applicants;
    private final ObjectProvider<FinanceControls> finance;

    @GetMapping("/summary")
    public Map<String, Object> summary(HttpServletRequest request, HttpServletResponse response) {
        LocalDate today = LocalDate.now();
        List<Employee> all = employees.findAll();
        List<Employee> active = all.stream().filter(e -> e.getStatus() == Employee.Status.ACTIVE).toList();

        Map<String, Object> out = new LinkedHashMap<>();

        // ---- headline numbers ----
        Map<String, Object> cards = new LinkedHashMap<>();
        cards.put("headcount", active.size());
        cards.put("pendingLeaves", leaveApplications.countByStatus(ApprovalStatus.PENDING));
        cards.put("pendingExpenses", claims.countByStatus(ApprovalStatus.PENDING));
        cards.put("openPositions", openings.findByStatus(JobOpening.Status.OPEN).stream()
                .mapToInt(o -> o.getVacancies() == null ? 1 : o.getVacancies()).sum());
        cards.put("applicantsInPipeline", applicants.countByStatusNotIn(
                List.of(JobApplicant.Status.HIRED, JobApplicant.Status.REJECTED)));
        out.put("cards", cards);

        // ---- headcount splits ----
        out.put("byDepartment", countBy(active, e -> e.getDepartment() == null ? "Unassigned" : e.getDepartment().getName()));
        out.put("byGender", countBy(active, e -> e.getGender() == null ? "Not set" : label(e.getGender().name())));
        out.put("byEmploymentType",
                countBy(active, e -> e.getEmploymentType() == null ? "Not set" : label(e.getEmploymentType().name())));

        // ---- joiners, leavers and attrition over the last 12 months ----
        List<YearMonth> months = new ArrayList<>();
        for (int i = MONTHS - 1; i >= 0; i--) {
            months.add(YearMonth.from(today).minusMonths(i));
        }
        List<String> labels = months.stream().map(YearMonth::toString).toList();
        List<Integer> joiners = new ArrayList<>();
        List<Integer> leavers = new ArrayList<>();
        int totalLeavers = 0;
        for (YearMonth ym : months) {
            int j = (int) all.stream()
                    .filter(e -> e.getDateOfJoining() != null && YearMonth.from(e.getDateOfJoining()).equals(ym)).count();
            int l = (int) all.stream()
                    .filter(e -> e.getRelievingDate() != null && YearMonth.from(e.getRelievingDate()).equals(ym)).count();
            joiners.add(j);
            leavers.add(l);
            totalLeavers += l;
        }
        Map<String, Object> movement = new LinkedHashMap<>();
        movement.put("labels", labels);
        movement.put("joiners", joiners);
        movement.put("leavers", leavers);
        out.put("movement", movement);
        // Attrition = people who left in the period / (current headcount + those who left).
        int denominator = active.size() + totalLeavers;
        cards.put("attritionRate", denominator == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(totalLeavers * 100.0 / denominator).setScale(1, RoundingMode.HALF_UP));

        // ---- approved leave days per month (counted in the month the leave starts) ----
        Map<YearMonth, BigDecimal> leaveDays = new TreeMap<>();
        months.forEach(m -> leaveDays.put(m, BigDecimal.ZERO));
        LocalDate earliestMonth = months.get(0).atDay(1);
        for (LeaveApplication la : leaveApplications.findByStatusAndFromDateGreaterThanEqual(
                ApprovalStatus.APPROVED, earliestMonth)) {
            YearMonth ym = YearMonth.from(la.getFromDate());
            if (leaveDays.containsKey(ym)) {
                leaveDays.merge(ym, la.getTotalDays(), BigDecimal::add);
            }
        }
        Map<String, Object> leaveTrend = new LinkedHashMap<>();
        leaveTrend.put("labels", labels);
        leaveTrend.put("days", new ArrayList<>(leaveDays.values()));
        out.put("leaveTrend", leaveTrend);

        // ---- attendance today ----
        Map<String, Integer> todayCounts = new LinkedHashMap<>();
        for (Attendance.Status s : Attendance.Status.values()) {
            todayCounts.put(label(s.name()), 0);
        }
        List<Attendance> todayRows = attendance.findByAttendanceDate(today);
        for (Attendance a : todayRows) {
            todayCounts.merge(label(a.getStatus().name()), 1, Integer::sum);
        }
        todayCounts.put("Not marked", Math.max(0, active.size() - todayRows.size()));
        out.put("attendanceToday", todayCounts);

        // ---- payroll cost per month ----
        var controls = finance.getIfAvailable();
        boolean payrollAllowed = controls == null || ((CurrentUser.isPlatformWorkspace()
                || CurrentUser.get().can("payroll:read")) && controls.unlocked(request));
        if (controls != null) response.setHeader("Cache-Control", "no-store");
        List<PayrollRun> runs = payrollAllowed ? new ArrayList<>(
                payrollRuns.findAllByOrderByPeriodYearDescPeriodMonthDesc(PageRequest.of(0, MONTHS)).getContent())
                : List.of();
        if (payrollAllowed) java.util.Collections.reverse(runs);
        Map<String, Object> payroll = new LinkedHashMap<>();
        payroll.put("labels", runs.stream()
                .map(r -> YearMonth.of(r.getPeriodYear(), r.getPeriodMonth()).toString()).toList());
        payroll.put("net", runs.stream().map(r -> r.getTotalNet() == null ? BigDecimal.ZERO : r.getTotalNet()).toList());
        out.put("payroll", payroll);
        out.put("payrollLocked", !payrollAllowed);

        // ---- birthdays in the next 30 days ----
        List<Map<String, Object>> birthdays = new ArrayList<>();
        for (Employee e : active) {
            if (e.getDateOfBirth() == null) {
                continue;
            }
            LocalDate next = nextBirthday(e.getDateOfBirth(), today);
            if (!next.isAfter(today.plusDays(30))) {
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("name", e.getFullName());
                b.put("date", next);
                birthdays.add(b);
            }
        }
        birthdays.sort((a, b) -> ((LocalDate) a.get("date")).compareTo((LocalDate) b.get("date")));
        out.put("birthdays", birthdays);

        return out;
    }

    private static LocalDate nextBirthday(LocalDate dob, LocalDate today) {
        // Feb 29 birthdays fall on Feb 28 in non-leap years.
        LocalDate thisYear = dob.withYear(today.getYear());
        return thisYear.isBefore(today) ? dob.withYear(today.getYear() + 1) : thisYear;
    }

    private static Map<String, Integer> countBy(List<Employee> list, java.util.function.Function<Employee, String> key) {
        Map<String, Integer> m = new TreeMap<>();
        for (Employee e : list) {
            m.merge(key.apply(e), 1, Integer::sum);
        }
        return m;
    }

    /** FULL_TIME -> "Full time" */
    private static String label(String enumName) {
        String s = enumName.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
