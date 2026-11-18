package com.hrms.platform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

import com.hrms.HrmsApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "HRMS_TEST_DATABASE_URL",
        matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/hrms_test")
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@SpringBootTest(classes = HrmsApplication.class, properties = {
        "spring.datasource.url=${HRMS_TEST_DATABASE_URL}",
        "spring.datasource.username=${HRMS_TEST_DATABASE_USERNAME:hrms_test}",
        "spring.datasource.password=${HRMS_TEST_DATABASE_PASSWORD:}",
        "app.platform.enabled=true",
        "app.platform.public-origin=https://hrms.example.invalid",
        "app.cors.allowed-origins=https://hrms.example.invalid",
        "app.tenancy.allow-additional-companies=true"
})
class PlatformOnboardingTest {
    private static final String OWNER_PASSWORD = fixtureCredential();
    private static final String COMPANY_PASSWORD = fixtureCredential();
    private static final String UPDATED_PASSWORD = fixtureCredential();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired Environment environment;
    @MockitoBean JavaMailSender mail;

    @Test
    void ownerOnboardsTwoIsolatedCompaniesAndCanRevokeAccess() throws Exception {
        mvc.perform(get("/api/platform/companies")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/platform/companies").contentType("application/json")
                .content(company("unauthenticated"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/platform/auth/login").contentType("application/json")
                .content("{\"username\":\"logavasu\",\"password\":\"%s\"}".formatted(fixtureCredential())))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/platform/session").header("Authorization", owner()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("logavasu"));

        create("first-tenant");
        create("second-tenant");
        var companies = read(mvc.perform(get("/api/platform/companies").header("Authorization", owner()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String firstId = companyId(companies, "first-tenant");
        String secondId = companyId(companies, "second-tenant");
        mvc.perform(post("/api/platform/companies/{id}/invitation/resend", secondId).header("Authorization", owner()))
                .andExpect(status().isOk());

        var sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, times(3)).send(sent.capture());
        String firstInvite = secret(sent.getAllValues().get(0));
        String superseded = secret(sent.getAllValues().get(1));
        String secondInvite = secret(sent.getAllValues().get(2));
        mvc.perform(post("/api/auth/accept-admin-invite").contentType("application/json")
                .content(acceptance(superseded))).andExpect(status().isBadRequest());
        accept(firstInvite, "first-tenant");
        accept(secondInvite, "second-tenant");
        mvc.perform(post("/api/auth/accept-admin-invite").contentType("application/json")
                .content(acceptance(firstInvite))).andExpect(status().isBadRequest());

        String first = signIn("first-tenant");
        String second = signIn("second-tenant");
        String record = department(first);
        String other = department(second);
        mvc.perform(get("/api/core/departments").param("page", "0").header("Authorization", second))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(Long.parseLong(other)));
        mvc.perform(get("/api/core/departments/{id}", record).header("Authorization", second))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/core/departments/{id}", record).header("Authorization", second)
                .contentType("application/json").content("{\"name\":\"Denied\"}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/core/departments/{id}", record).header("Authorization", second))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/platform/companies").header("Authorization", second)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/core/departments").header("Authorization", owner())).andExpect(status().isUnauthorized());
        verifyCompanyWorkflows(first, second);

        mvc.perform(post("/api/platform/companies/{id}/suspend", firstId).header("Authorization", owner()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", first)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/platform/companies/{id}/reactivate", firstId).header("Authorization", owner()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", first)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", signIn("first-tenant"))).andExpect(status().isOk());
        mvc.perform(post("/api/platform/logout").header("Authorization", owner())).andExpect(status().isOk());
        mvc.perform(get("/api/platform/session").header("Authorization", owner())).andExpect(status().isUnauthorized());
    }

    private void verifyCompanyWorkflows(String first, String second) throws Exception {
        for (String endpoint : new String[] {
                "/dashboard/summary", "/core/branches", "/core/designations", "/attendance/shifts",
                "/leave/types", "/leave/allocations", "/recruitment/openings", "/recruitment/applicants",
                "/recruitment/interviews", "/performance/cycles", "/performance/goals", "/performance/appraisals",
                "/admin/users", "/admin/roles", "/admin/permissions", "/admin/audit-logs", "/notifications"}) {
            mvc.perform(get("/api" + endpoint).header("Authorization", first)).andExpect(status().isOk());
        }
        long manager = createRecord(first, "/core/employees", """
                {"firstName":"Manager","dateOfJoining":"2025-01-01","bankAccountNo":"fixture-bank-account"}
                """).get("id").asLong();
        long employee = createRecord(first, "/core/employees", """
                {"firstName":"Employee","dateOfJoining":"2025-01-01","reportsTo":{"id":%d}}
                """.formatted(manager)).get("id").asLong();
        mvc.perform(get("/api/core/employees/{id}", employee).header("Authorization", first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reportsTo.fullName").value("Manager"))
                .andExpect(jsonPath("$.reportsTo.bankAccountNo").doesNotHaveJsonPath());
        mvc.perform(get("/api/core/employees/{id}", employee).header("Authorization", second))
                .andExpect(status().isNotFound());

        String today = java.time.LocalDate.now().toString();
        long attendance = createRecord(first, "/attendance/records", """
                {"employee":{"id":%d},"attendanceDate":"%s","status":"PRESENT","inTime":"09:00","outTime":"17:00"}
                """.formatted(employee, today)).get("id").asLong();
        mvc.perform(get("/api/attendance/records/{id}", attendance).header("Authorization", second))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/attendance/records/{id}", attendance).header("Authorization", first)
                .contentType("application/json").content("""
                        {"employee":{"id":%d},"attendanceDate":"%s","status":"PRESENT","inTime":"09:00"}
                        """.formatted(employee, today)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workingHours").isEmpty());
        mvc.perform(get("/api/attendance/summary").param("month", "13").header("Authorization", first))
                .andExpect(status().isBadRequest());

        long advance = createRecord(first, "/expense/advances", """
                {"employee":{"id":%d},"advanceDate":"2025-02-01","amount":100}
                """.formatted(employee)).get("id").asLong();
        mvc.perform(post("/api/expense/advances/{id}/pay", advance).header("Authorization", first))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/expense/advances/{id}/approve", advance).header("Authorization", first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(post("/api/expense/advances/{id}/pay", advance).header("Authorization", first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
        mvc.perform(get("/api/expense/advances/{id}", advance).header("Authorization", second))
                .andExpect(status().isNotFound());

        String list = """
                {"name":"Calendar","fromDate":"2025-01-01","toDate":"2025-12-31",
                 "holidays":[{"holidayDate":"2025-01-01","description":"New year"}]}
                """;
        var calendar = createRecord(first, "/leave/holiday-lists", list);
        long holiday = calendar.get("holidays").get(0).get("id").asLong();
        long otherCalendar = createRecord(first, "/leave/holiday-lists", list.replace("Calendar", "Other calendar")).get("id").asLong();
        String edited = list.replace("{\"holidayDate\"", "{\"id\":" + holiday + ",\"holidayDate\"");
        mvc.perform(put("/api/leave/holiday-lists/{id}", calendar.get("id").asLong()).header("Authorization", first)
                .contentType("application/json").content(edited)).andExpect(status().isOk());
        mvc.perform(put("/api/leave/holiday-lists/{id}", otherCalendar).header("Authorization", first)
                .contentType("application/json").content(edited)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/leave/holiday-lists").header("Authorization", second)
                .contentType("application/json").content(edited)).andExpect(status().isBadRequest());

        long role = createRecord(first, "/admin/roles", "{\"name\":\"Employee self-service\",\"permissions\":[\"leave:read\"]}")
                .get("id").asLong();
        String worker = createWorker(first, role, employee, "worker");
        String coworker = createWorker(first, role, manager, "coworker");
        mvc.perform(get("/api/me/profile").header("Authorization", worker))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(employee));
        mvc.perform(get("/api/admin/users").header("Authorization", worker)).andExpect(status().isForbidden());
        mvc.perform(get("/api/leave/applications").header("Authorization", worker)).andExpect(status().isOk());
        mvc.perform(post("/api/leave/types").header("Authorization", worker).contentType("application/json")
                .content("{\"name\":\"Forbidden\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/me/check-in").header("Authorization", worker))
                .andExpect(status().isOk()).andExpect(jsonPath("$.inTime").isNotEmpty());
        mvc.perform(post("/api/me/check-out").header("Authorization", worker))
                .andExpect(status().isOk()).andExpect(jsonPath("$.outTime").isNotEmpty());

        verifyLeave(first, second, worker, coworker, employee);
        verifyExpenses(first, second, worker, employee);
        verifyPayroll(first, second, worker, employee);
        verifyRecruitment(first, second, employee);
        verifyPerformance(first, second, worker, coworker, employee);
        verifyNotifications(first, second, worker, coworker);
        mvc.perform(get("/api/admin/audit-logs").header("Authorization", first).param("page", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].username").isNotEmpty());
        mvc.perform(post("/api/auth/change-password").header("Authorization", worker)
                .contentType("application/json")
                .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(COMPANY_PASSWORD, UPDATED_PASSWORD)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(companyLogin("first-tenant", "worker", COMPANY_PASSWORD)))
                .andExpect(status().isUnauthorized());
        var newLogin = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(companyLogin("first-tenant", "worker", UPDATED_PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        String newToken = "Bearer " + read(newLogin.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post("/api/auth/logout").header("Authorization", newToken)).andExpect(status().isOk());
        mvc.perform(get("/api/me/profile").header("Authorization", newToken)).andExpect(status().isUnauthorized());
    }

    private String createWorker(String admin, long role, long employee, String username) throws Exception {
        createRecord(admin, "/admin/users", """
                {"username":"%s","password":"%s","employee":{"id":%d},"role":{"id":%d},"active":true}
                """.formatted(username, COMPANY_PASSWORD, employee, role));
        return signIn("first-tenant", username);
    }

    private void verifyLeave(String first, String second, String worker, String coworker, long employee) throws Exception {
        long leaveType = createRecord(first, "/leave/types", "{\"name\":\"Annual leave\"}").get("id").asLong();
        int year = java.time.LocalDate.now().getYear();
        createRecord(first, "/leave/allocations", """
                {"employee":{"id":%d},"leaveType":{"id":%d},"days":10,"fromDate":"%d-01-01","toDate":"%d-12-31"}
                """.formatted(employee, leaveType, year, year));
        String leaveBody = """
                {"leaveType":{"id":%d},"fromDate":"%d-02-17","toDate":"%d-02-17","halfDay":true}
                """.formatted(leaveType, year, year);
        long worked = createRecord(first, "/attendance/records", """
                {"employee":{"id":%d},"attendanceDate":"%d-02-17","status":"PRESENT",
                 "inTime":"09:00","outTime":"17:00","remarks":"Worked onsite"}
                """.formatted(employee, year)).get("id").asLong();
        long id = createRecord(worker, "/me/leaves", leaveBody).get("id").asLong();
        mvc.perform(post("/api/me/leaves/{id}/cancel", id).header("Authorization", coworker))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/leave/applications/{id}/approve", id).header("Authorization", second))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/leave/applications/{id}/approve", id).header("Authorization", first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(get("/api/me/leave-balance").header("Authorization", worker))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].balance").value(9.5));
        mvc.perform(post("/api/me/leaves/{id}/cancel", id).header("Authorization", worker))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get("/api/attendance/records/{id}", worked).header("Authorization", first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workingHours").value(8.0))
                .andExpect(jsonPath("$.remarks").value("Worked onsite"));
        mvc.perform(get("/api/me/leave-balance").header("Authorization", worker))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].balance").value(10));
        long rejected = createRecord(worker, "/me/leaves", leaveBody).get("id").asLong();
        mvc.perform(post("/api/leave/applications/{id}/reject", rejected).header("Authorization", first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        long generated = createRecord(worker, "/me/leaves", leaveBody.replace("-02-17", "-02-18")).get("id").asLong();
        mvc.perform(post("/api/leave/applications/{id}/approve", generated).header("Authorization", first))
                .andExpect(status().isOk());
        var attendance = mvc.perform(get("/api/me/attendance").header("Authorization", worker)
                .param("year", Integer.toString(year)).param("month", "2"))
                .andExpect(status().isOk()).andReturn();
        long generatedAttendance = 0;
        for (JsonNode row : read(attendance.getResponse().getContentAsString())) {
            if (row.get("attendanceDate").asText().equals(year + "-02-18")) {
                generatedAttendance = row.get("id").asLong();
                org.junit.jupiter.api.Assertions.assertEquals("HALF_DAY", row.get("status").asText());
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(generatedAttendance > 0);
        mvc.perform(post("/api/me/leaves/{id}/cancel", generated).header("Authorization", worker))
                .andExpect(status().isOk());
        mvc.perform(get("/api/attendance/records/{id}", generatedAttendance).header("Authorization", first))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/me/leaves").header("Authorization", coworker).param("page", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    private void verifyExpenses(String first, String second, String worker, long employee) throws Exception {
        long expenseType = createRecord(first, "/expense/types", "{\"name\":\"Travel\"}").get("id").asLong();
        String claimBody = """
                {"claimDate":"2025-02-17","lines":[{"expenseType":{"id":%d},"amount":125.50},{"expenseType":{"id":%d},"amount":25}]}
                """.formatted(expenseType, expenseType);
        JsonNode claim = createRecord(worker, "/me/expenses", claimBody);
        org.junit.jupiter.api.Assertions.assertEquals(150.5, claim.get("totalAmount").asDouble());
        long id = claim.get("id").asLong();
        mvc.perform(get("/api/expense/claims/{id}", id).header("Authorization", second)).andExpect(status().isNotFound());
        mvc.perform(post("/api/expense/claims/{id}/pay", id).header("Authorization", first))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/expense/claims/{id}/approve", id).header("Authorization", first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(post("/api/expense/claims/{id}/pay", id).header("Authorization", first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
        mvc.perform(delete("/api/expense/claims/{id}", id).header("Authorization", first))
                .andExpect(status().isBadRequest());
        long rejected = createRecord(first, "/expense/claims", claimBody.replaceFirst("\\{", "{\"employee\":{\"id\":" + employee + "},"))
                .get("id").asLong();
        mvc.perform(post("/api/expense/claims/{id}/reject", rejected).header("Authorization", first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(delete("/api/expense/claims/{id}", rejected).header("Authorization", first)).andExpect(status().isOk());
    }

    private void verifyPayroll(String first, String second, String worker, long employee) throws Exception {
        long earning = createRecord(first, "/payroll/components", """
                {"name":"Base","type":"EARNING","dependsOnPaymentDays":false}
                """).get("id").asLong();
        long deduction = createRecord(first, "/payroll/components", """
                {"name":"Deduction","type":"DEDUCTION","dependsOnPaymentDays":false}
                """).get("id").asLong();
        long structure = createRecord(first, "/payroll/structures", """
                {"name":"Salary","lines":[{"component":{"id":%d},"amountType":"PERCENT_OF_BASE","amountValue":100},
                  {"component":{"id":%d},"amountType":"PERCENT_OF_GROSS","amountValue":10}]}
                """.formatted(earning, deduction)).get("id").asLong();
        createRecord(first, "/payroll/assignments", """
                {"employee":{"id":%d},"structure":{"id":%d},"baseAmount":5000,"fromDate":"2025-01-01"}
                """.formatted(employee, structure));
        String period = "{\"year\":2025,\"month\":2}";
        long run = createRecord(first, "/payroll/runs", period).get("id").asLong();
        org.junit.jupiter.api.Assertions.assertEquals(run, createRecord(first, "/payroll/runs", period).get("id").asLong());
        mvc.perform(get("/api/payroll/runs/{id}/slips", run).header("Authorization", first).param("page", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].grossPay").value(5000.0))
                .andExpect(jsonPath("$.content[0].totalDeduction").value(500.0))
                .andExpect(jsonPath("$.content[0].netPay").value(4500.0));
        mvc.perform(get("/api/me/payslips").header("Authorization", worker).param("page", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/payroll/runs").header("Authorization", second).param("page", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(post("/api/payroll/runs/{id}/submit", run).header("Authorization", second))
                .andExpect(status().isNotFound());
        expectSingleSuccess(
                post("/api/payroll/runs/{id}/submit", run).header("Authorization", first),
                post("/api/payroll/runs/{id}/submit", run).header("Authorization", first));
        mvc.perform(get("/api/me/payslips").header("Authorization", worker).param("page", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].netPay").value(4500.0));
        mvc.perform(delete("/api/payroll/runs/{id}", run).header("Authorization", first))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/payroll/runs").header("Authorization", first).contentType("application/json").content(period))
                .andExpect(status().isBadRequest());
        long draft = createRecord(first, "/payroll/runs", "{\"year\":2025,\"month\":3}").get("id").asLong();
        mvc.perform(delete("/api/payroll/runs/{id}", draft).header("Authorization", first)).andExpect(status().isOk());
    }

    private void verifyRecruitment(String first, String second, long employee) throws Exception {
        long opening = createRecord(first, "/recruitment/openings", "{\"title\":\"Engineer\",\"vacancies\":1}").get("id").asLong();
        long applicant = createRecord(first, "/recruitment/applicants", """
                {"name":"Test Applicant","jobOpening":{"id":%d}}
                """.formatted(opening)).get("id").asLong();
        createRecord(first, "/recruitment/interviews", """
                {"applicant":{"id":%d},"interviewer":{"id":%d},"scheduledAt":"2026-11-20T12:00","mode":"VIDEO"}
                """.formatted(applicant, employee));
        mvc.perform(post("/api/recruitment/applicants/{id}/hire", applicant).header("Authorization", second))
                .andExpect(status().isNotFound());
        expectSingleSuccess(
                post("/api/recruitment/applicants/{id}/hire", applicant).header("Authorization", first),
                post("/api/recruitment/applicants/{id}/hire", applicant).header("Authorization", first));
        mvc.perform(get("/api/recruitment/applicants/{id}", applicant).header("Authorization", first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("HIRED"));
        mvc.perform(post("/api/recruitment/applicants/{id}/hire", applicant).header("Authorization", first))
                .andExpect(status().isBadRequest());
    }

    private void expectSingleSuccess(MockHttpServletRequestBuilder first, MockHttpServletRequestBuilder second) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.<java.util.concurrent.Callable<Integer>>of(
                    () -> mvc.perform(first).andReturn().getResponse().getStatus(),
                    () -> mvc.perform(second).andReturn().getResponse().getStatus()));
            var statuses = List.of(results.get(0).get(), results.get(1).get()).stream().sorted().toList();
            org.junit.jupiter.api.Assertions.assertEquals(List.of(200, 400), statuses);
        }
    }

    private void verifyPerformance(String first, String second, String worker, String coworker, long employee) throws Exception {
        long cycle = createRecord(first, "/performance/cycles", "{\"name\":\"Review\"}").get("id").asLong();
        long goal = createRecord(first, "/performance/goals", """
                {"employee":{"id":%d},"cycle":{"id":%d},"title":"Complete training","weightage":100}
                """.formatted(employee, cycle)).get("id").asLong();
        mvc.perform(post("/api/me/goals/{id}/progress", goal).header("Authorization", coworker)
                .contentType("application/json").content("{\"progress\":80}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/me/goals/{id}/progress", goal).header("Authorization", worker)
                .contentType("application/json").content("{\"progress\":80}")).andExpect(status().isOk());
        long appraisal = createRecord(first, "/performance/appraisals", """
                {"employee":{"id":%d},"cycle":{"id":%d},"managerRating":5}
                """.formatted(employee, cycle)).get("id").asLong();
        mvc.perform(post("/api/me/appraisals/{id}/self-rating", appraisal).header("Authorization", worker)
                .contentType("application/json").content("{\"selfRating\":4}")).andExpect(status().isOk());
        mvc.perform(post("/api/performance/appraisals/{id}/submit", appraisal).header("Authorization", second))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/performance/appraisals/{id}/submit", appraisal).header("Authorization", first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FINALIZED"))
                .andExpect(jsonPath("$.finalScore").value(4.7));
        mvc.perform(post("/api/me/appraisals/{id}/self-rating", appraisal).header("Authorization", worker)
                .contentType("application/json").content("{\"selfRating\":5}")).andExpect(status().isBadRequest());
    }

    private void verifyNotifications(String first, String second, String worker, String coworker) throws Exception {
        var response = mvc.perform(get("/api/notifications").header("Authorization", worker).param("page", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").isNumber()).andReturn();
        long id = read(response.getResponse().getContentAsString()).get("items").get(0).get("id").asLong();
        mvc.perform(post("/api/notifications/{id}/read", id).header("Authorization", second)).andExpect(status().isNotFound());
        mvc.perform(post("/api/notifications/{id}/read", id).header("Authorization", coworker)).andExpect(status().isForbidden());
        mvc.perform(post("/api/notifications/{id}/read", id).header("Authorization", worker)).andExpect(status().isOk());
        mvc.perform(post("/api/notifications/read-all").header("Authorization", worker)).andExpect(status().isOk());
        mvc.perform(get("/api/notifications").header("Authorization", worker)).andExpect(jsonPath("$.unread").value(0));
        mvc.perform(get("/api/notifications").header("Authorization", first)).andExpect(status().isOk());
    }

    private JsonNode createRecord(String token, String path, String body) throws Exception {
        var result = mvc.perform(post("/api" + path).header("Authorization", token)
                .contentType("application/json").content(body)).andExpect(status().isOk()).andReturn();
        return read(result.getResponse().getContentAsString());
    }

    @Test
    void failedDeliveryCanBeRetriedAndRevoked() throws Exception {
        doThrow(new org.springframework.mail.MailSendException("Test delivery failure"))
                .doNothing().when(mail).send(any(SimpleMailMessage.class));
        mvc.perform(post("/api/platform/companies").header("Authorization", owner()).contentType("application/json")
                .content(company("delivery-retry"))).andExpect(status().isOk()).andExpect(jsonPath("$.delivered").value(false));
        var companies = read(mvc.perform(get("/api/platform/companies").header("Authorization", owner()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String id = companyId(companies, "delivery-retry");
        mvc.perform(get("/api/platform/companies/{id}", id).header("Authorization", owner()))
                .andExpect(jsonPath("$.delivery_status").value("FAILED"));
        mvc.perform(post("/api/platform/companies/{id}/invitation/resend", id).header("Authorization", owner()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.delivered").value(true));
        mvc.perform(post("/api/platform/companies/{id}/invitation/revoke", id).header("Authorization", owner()))
                .andExpect(status().isOk());
        var sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, times(2)).send(sent.capture());
        mvc.perform(post("/api/auth/accept-admin-invite").contentType("application/json")
                .content(acceptance(secret(sent.getAllValues().getLast())))).andExpect(status().isBadRequest());
    }

    @Test
    void browserCompanyAuthAllowsOnlyConfiguredFrontendOrigins() throws Exception {
        mvc.perform(options("/api/auth/accept-admin-invite")
                .header("Origin", "https://hrms.example.invalid").header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://hrms.example.invalid"));
        mvc.perform(options("/api/auth/accept-admin-invite")
                .header("Origin", "https://other.example.invalid").header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void productionServletCookiesUseSecureDefaults() {
        org.junit.jupiter.api.Assertions.assertEquals("true",
                environment.getProperty("server.servlet.session.cookie.http-only"));
        org.junit.jupiter.api.Assertions.assertEquals("true",
                environment.getProperty("server.servlet.session.cookie.secure"));
        org.junit.jupiter.api.Assertions.assertEquals("lax",
                environment.getProperty("server.servlet.session.cookie.same-site"));
    }

    @Test
    void expiredInvitationCannotActivateCompanyOrCreateAnAdmin() throws Exception {
        create("expired-invite");
        var companies = read(mvc.perform(get("/api/platform/companies").header("Authorization", owner()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        UUID id = UUID.fromString(companyId(companies, "expired-invite"));
        var sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(sent.capture());
        int updated = jdbc.update("""
                update company_admin_invitation
                set created_at = current_timestamp - interval '49 hours',
                    expires_at = current_timestamp - interval '1 minute'
                where tenant_id = ?
                """, id);
        org.junit.jupiter.api.Assertions.assertEquals(1, updated);

        mvc.perform(get("/api/platform/companies/{id}", id).header("Authorization", owner()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.invitation_status").value("EXPIRED"));
        mvc.perform(post("/api/auth/accept-admin-invite").contentType("application/json")
                .content(acceptance(secret(sent.getValue())))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/platform/companies/{id}", id).header("Authorization", owner()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.invitation_status").value("EXPIRED"));
        org.junit.jupiter.api.Assertions.assertEquals(0L,
                jdbc.queryForObject("select count(*) from app_user where tenant_id = ?", Long.class, id));
    }

    private void create(String code) throws Exception {
        mvc.perform(post("/api/platform/companies").header("Authorization", owner()).contentType("application/json")
                .content(company(code))).andExpect(status().isOk()).andExpect(jsonPath("$.delivered").value(true));
    }

    private String company(String code) {
        return """
                {"company":{"name":"Test company","code":"%s","contactEmail":"admin@example.invalid",
                "timezone":"UTC","currency":"USD"},"adminEmail":"admin@example.invalid"}
                """.formatted(code);
    }

    private String companyId(JsonNode response, String code) {
        for (JsonNode item : response.get("content")) {
            if (code.equals(item.get("code").asText())) return item.get("id").asText();
        }
        throw new AssertionError("Company not found");
    }

    private String secret(SimpleMailMessage message) {
        return message.getText().split("#", 2)[1];
    }

    private String acceptance(String secret) {
        return "{\"token\":\"%s\",\"password\":\"%s\"}".formatted(secret, COMPANY_PASSWORD);
    }

    private void accept(String secret, String company) throws Exception {
        mvc.perform(post("/api/auth/accept-admin-invite").contentType("application/json").content(acceptance(secret)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.companyCode").value(company));
    }

    private String signIn(String company) throws Exception {
        return signIn(company, "admin@example.invalid");
    }

    private String signIn(String company, String username) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(companyLogin(company, username, COMPANY_PASSWORD))).andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        return "Bearer " + read(result.getResponse().getContentAsString()).get("token").asText();
    }

    private static String companyLogin(String company, String username, String password) {
        return """
                {"companyCode":"%s","username":"%s","password":"%s"}
                """.formatted(company, username, password);
    }

    private String department(String auth) throws Exception {
        var result = mvc.perform(post("/api/core/departments").header("Authorization", auth).contentType("application/json")
                .content("{\"name\":\"Shared department name\"}")).andExpect(status().isOk()).andReturn();
        return read(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String ownerToken;

    @DynamicPropertySource
    static void bootstrap(DynamicPropertyRegistry properties) throws Exception {
        Path file = Files.createTempFile("hrms-owner-test-", ".txt");
        file.toFile().deleteOnExit();
        Files.writeString(file, OWNER_PASSWORD);
        properties.add("app.platform.bootstrap-password-file", file::toString);
        String companyKey = fixtureCredential();
        String platformKey = fixtureCredential();
        properties.add("app.jwt.secret", () -> companyKey);
        properties.add("app.platform.jwt-secret", () -> platformKey);
    }

    private static String fixtureCredential() {
        byte[] bytes = new byte[36];
        new SecureRandom().nextBytes(bytes);
        // Standard Base64 works both as a JSON-safe password and as a platform signing key.
        return Base64.getEncoder().encodeToString(bytes);
    }

    private String owner() throws Exception {
        if (ownerToken == null) {
            var result = mvc.perform(post("/api/platform/auth/login").contentType("application/json")
                    .content("""
                            {"username":"logavasu","password":"%s"}
                            """.formatted(OWNER_PASSWORD))).andExpect(status().isOk())
                    .andExpect(header().doesNotExist("Set-Cookie")).andReturn();
            ownerToken = "Bearer " + read(result.getResponse().getContentAsString()).get("token").asText();
        }
        return ownerToken;
    }

    private JsonNode read(String response) throws Exception {
        return json.readTree(response);
    }
}
