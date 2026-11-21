package com.hrms.shared.security;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.modules.audit.interceptor.AuditInterceptor;
import com.hrms.shared.common.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

public class FinanceFilter extends OncePerRequestFilter {
    private static final int MAX_BODY = 128 * 1024;
    private static final Set<String> WRITES = Set.of("POST", "PUT", "DELETE");
    private final FinanceControls finance;
    private final ObjectMapper json;
    private final AuditInterceptor audit;

    public FinanceFilter(FinanceControls finance, ObjectMapper json, AuditInterceptor audit) {
        this.finance = finance;
        this.json = json;
        this.audit = audit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return "OPTIONS".equals(request.getMethod()) || !(path.startsWith("/api/payroll/")
                || path.matches("/api/core/employees/[^/]+/bank-details/?")
                || path.equals("/api/me/payslips") || path.startsWith("/api/sensitive/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        UUID correlation = com.hrms.modules.audit.interceptor.AuditCapture.requestId(request);
        request.setAttribute(FinanceControls.class.getName(), correlation);
        response.setHeader("X-Request-Id", correlation.toString());
        response.setHeader("Cache-Control", "no-store");
        var actor = finance.actor(request);
        String path = request.getServletPath();
        String method = request.getMethod();
        boolean write = WRITES.contains(method);
        if (path.equals("/api/sensitive/step-up") && method.equals("POST")) {
            chain.doFilter(request, response);
            return;
        }
        try {
            if (path.endsWith("/bank-details") && !CurrentUser.hasExplicitPermission(
                    write ? "employee-bank:write" : "employee-bank:read")) {
                throw ApiException.forbidden("An explicit banking permission is required.");
            }
            if (!finance.unlocked(actor)) {
                reply(response, 428, Map.of("code", "STEP_UP_REQUIRED", "message", "Verify your password to unlock financial access."));
            } else if (write && !path.startsWith("/api/sensitive/")) {
                if (!supportedChange(method, path) || request.getQueryString() != null) {
                    throw ApiException.badRequest("Unsupported financial change.");
                }
                byte[] body = request.getInputStream().readNBytes(MAX_BODY + 1);
                if (body.length > MAX_BODY) {
                    reply(response, 413, Map.of("message", "Financial change exceeds the permitted size."));
                } else if (!"true".equals(request.getHeader("X-HRMS-Confirm"))) {
                    reply(response, 428, Map.of("code", "CONFIRMATION_REQUIRED", "message", "Confirm and provide a reason for this financial change."));
                } else {
                    var review = finance.requestApproval(actor, method, path, body, request.getContentType(),
                            request.getHeader("X-HRMS-Reason"));
                    if ("APPROVED".equals(review.status())) {
                        chain.doFilter(repeatable(request, body), response);
                        if (response.getStatus() < 400) {
                            finance.execute(actor, review);
                            finance.event(actor, "CHANGE_EXECUTED", review.id(), request, response.getStatus());
                        }
                    } else {
                        finance.event(actor, "CHANGE_QUEUED", review.id(), request, 202);
                        reply(response, 202, Map.of("code", "REVIEW_REQUIRED", "approvalId", review.id(), "message",
                                "A different authorized reviewer must approve this change. Repeat the same change after approval."));
                    }
                }
            } else {
                chain.doFilter(request, response);
                if (!write && response.getStatus() < 400) {
                    finance.event(actor, "FINANCIAL_READ", null, request, response.getStatus());
                }
            }
        } catch (ApiException failure) {
            reply(response, failure.getStatus().value(), Map.of("message", failure.getMessage()));
        }
        if (response.getStatus() >= 400) {
            audit.deferFinancialDenial(request, response.getStatus(), correlation);
        }
    }

    private static boolean supportedChange(String method, String path) {
        String catalog = "/api/payroll/(components|structures|assignments)";
        return switch (method) {
            case "POST" -> path.matches(catalog + "|/api/payroll/runs(/[0-9]+/submit)?");
            case "PUT" -> path.matches(catalog + "/[0-9]+|/api/core/employees/[0-9]+/bank-details");
            case "DELETE" -> path.matches("/api/payroll/(components|structures|assignments|runs)/[0-9]+");
            default -> false;
        };
    }

    private static HttpServletRequest repeatable(HttpServletRequest request, byte[] body) {
        return new HttpServletRequestWrapper(request) {
            @Override
            public ServletInputStream getInputStream() {
                var input = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    @Override public int read() { return input.read(); }
                    @Override public int read(byte[] data, int off, int len) { return input.read(data, off, len); }
                    @Override public boolean isFinished() { return input.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                };
            }
        };
    }

    private void reply(HttpServletResponse response, int status, Map<String, ?> data) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        json.writeValue(response.getOutputStream(), data);
    }
}
