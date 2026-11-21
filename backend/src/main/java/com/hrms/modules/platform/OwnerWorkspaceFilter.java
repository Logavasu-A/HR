package com.hrms.modules.platform;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.modules.audit.interceptor.AuditInterceptor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

public class OwnerWorkspaceFilter extends OncePerRequestFilter {
    private static final String HEADER = "X-HRMS-Workspace";
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "DELETE");
    private final OwnerWorkspaces workspaces;
    private final AuditInterceptor audit;
    private final ObjectMapper json;
    private final boolean finance;

    public OwnerWorkspaceFilter(OwnerWorkspaces workspaces, AuditInterceptor audit, ObjectMapper json, boolean finance) {
        this.workspaces = workspaces;
        this.audit = audit;
        this.json = json;
        this.finance = finance;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/api/") || request.getHeader(HEADER) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var event = OwnerWorkspaceController.describe(request);
        response.setHeader("X-Request-Id", event.id().toString());
        response.setHeader("Cache-Control", "no-store");
        String auth = request.getHeader("Authorization");
        var owner = auth != null && auth.startsWith("Bearer ") ? workspaces.authenticate(auth.substring(7)) : null;
        if (owner == null) {
            reject(response, 401, "A valid platform owner session is required.");
            return;
        }
        UUID grant;
        try {
            String supplied = request.getHeader(HEADER);
            grant = UUID.fromString(supplied);
            if (!grant.toString().equals(supplied)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            workspaces.record(owner, null, null, "WORKSPACE_REQUEST_DENIED", null, event, 400);
            reject(response, 400, "Invalid Admin View grant.");
            return;
        }
        var selected = workspaces.resolve(owner, grant, false);
        if (selected == null) {
            workspaces.record(owner, null, grant, "WORKSPACE_REQUEST_DENIED", null, event, 410);
            reject(response, 410, OwnerWorkspaces.replaced().getMessage());
            return;
        }

        var buffered = new ContentCachingResponseWrapper(response);
        var previous = SecurityContextHolder.getContext();
        try {
            workspaces.scope().transaction(selected.companyId(), tx -> {
                try {
                    var current = workspaces.resolve(owner, grant, true);
                    if (current == null || !selected.companyId().equals(current.companyId())) {
                        reject(buffered, 410, OwnerWorkspaces.replaced().getMessage());
                    } else {
                        String denied = denial(owner, current, request);
                        if (denied != null) reject(buffered, 403, denied);
                        else authorized(owner, current, request, buffered, chain);
                    }
                    if (buffered.getStatus() >= 400) tx.setRollbackOnly();
                    else workspaces.record(owner, current, grant, "WORKSPACE_REQUEST", null, event, buffered.getStatus());
                    return null;
                } catch (IOException | ServletException e) {
                    throw new RequestFailure(e);
                }
            });
        } catch (RequestFailure e) {
            workspaces.record(owner, selected, grant, "WORKSPACE_REQUEST_DENIED", null, event, 500);
            if (e.getCause() instanceof IOException io) throw io;
            throw (ServletException) e.getCause();
        } catch (RuntimeException e) {
            workspaces.record(owner, selected, grant, "WORKSPACE_REQUEST_DENIED", null, event, 500);
            throw e;
        } finally {
            SecurityContextHolder.setContext(previous);
            audit.flushRejected(request, selected.companyId());
        }
        if (buffered.getStatus() >= 400) {
            workspaces.record(owner, selected, grant, "WORKSPACE_REQUEST_DENIED", null, event, buffered.getStatus());
        }
        buffered.copyBodyToResponse();
    }

    private void authorized(PlatformAuth.Owner owner, OwnerWorkspaces.Workspace workspace,
                            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new OwnerWorkspacePrincipal(owner, workspace.companyId()), null, List.of()));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }

    private String denial(PlatformAuth.Owner owner, OwnerWorkspaces.Workspace workspace,
                                 HttpServletRequest request) {
        String path = request.getServletPath();
        String[] parts = path.split("/");
        String method = request.getMethod();
        if (parts.length < 3 || !OwnerWorkspacePrincipal.supports(parts[2]) || !METHODS.contains(method)) {
            return "This API is unavailable in Admin View.";
        }
        boolean read = "GET".equals(method);
        if (!read && !"ACTIVE".equals(workspace.status()) && !path.equals("/api/sensitive/step-up")) {
            return "This company is available for read-only inspection.";
        }
        boolean banking = "core".equals(parts[2]) && path.startsWith("/api/core/employees/")
                && (path.endsWith("/bank-details") || path.endsWith("/bank-details/"));
        if (((!finance && ("payroll".equals(parts[2]) || banking)) || ("admin".equals(parts[2]) && !read))
                && !OwnerWorkspaces.fresh(owner)) {
            return "Sign in again to access this sensitive operation in Admin View.";
        }
        return null;
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        json.writeValue(response.getOutputStream(), Map.of("message", message));
    }

    private static class RequestFailure extends RuntimeException {
        RequestFailure(Exception cause) {
            super(cause);
        }
    }
}
