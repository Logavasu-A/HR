package com.hrms.shared.security;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.hrms.modules.admin.model.AppUser;
import com.hrms.modules.admin.repository.UserRepo;
import com.hrms.modules.audit.interceptor.AuditInterceptor;
import com.hrms.modules.audit.interceptor.AuditCapture;
import com.hrms.shared.tenancy.CompanyTenancy;
import com.hrms.shared.tenancy.TenantDatabaseScope;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Reads the "Authorization: Bearer ..." header and signs the user in for this request. */
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final UserRepo users;
    private final CompanySessions sessions;
    private final TenantDatabaseScope tenantScope;
    private final AuditInterceptor audit;
    private final AuditCapture capture;

    public JwtFilter(JwtService jwt, UserRepo users, CompanySessions sessions, TenantDatabaseScope tenantScope,
                     AuditInterceptor audit, AuditCapture capture) {
        this.jwt = jwt;
        this.users = users;
        this.sessions = sessions;
        this.tenantScope = tenantScope;
        this.audit = audit;
        this.capture = capture;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return switch (request.getServletPath()) {
            case "/api/auth/login", "/api/auth/accept-admin-invite" -> true;
            default -> false;
        };
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            JwtService.CompanyToken token = jwt.parseCompanyToken(header.substring(7));
            if (token != null && sessions.isActive(token)) {
                if (tenantScope.enabled()) {
                    inDatabaseScope(request, response, chain, token);
                } else {
                    CompanyTenancy.attach(token.tenantId());
                    try {
                        authenticated(request, response, chain, token);
                    } finally {
                        CompanyTenancy.clear();
                    }
                }
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void inDatabaseScope(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
                                 JwtService.CompanyToken token) throws IOException, ServletException {
        var buffered = new ContentCachingResponseWrapper(response);
        if (capture != null) capture.start(request, buffered);
        int outcome = 500;
        try {
            tenantScope.transaction(token.tenantId(), token.userId(), status -> {
                try {
                    authenticated(request, buffered, chain, token);
                } catch (IOException | ServletException e) {
                    throw new RequestFailure(e);
                }
                if (buffered.getStatus() >= 400) status.setRollbackOnly();
                return null;
            });
            outcome = buffered.getStatus();
        } catch (RequestFailure failure) {
            if (failure.getCause() instanceof IOException io) throw io;
            throw (ServletException) failure.getCause();
        } finally {
            audit.flushRejected(request, token.tenantId());
            if (capture != null && SecurityContextHolder.getContext().getAuthentication() != null
                    && SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof AuthUser user) {
                capture.company(user, request, outcome);
            }
        }
        buffered.copyBodyToResponse();
    }

    private void authenticated(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
                               JwtService.CompanyToken token) throws IOException, ServletException {
        users.findById(token.userId()).filter(AppUser::isActive).ifPresent(user -> signIn(user, token));
        chain.doFilter(request, response);
    }

    private static class RequestFailure extends RuntimeException {
        RequestFailure(Exception cause) {
            super(cause);
        }
    }

    private void signIn(AppUser u, JwtService.CompanyToken token) {
        Set<String> perms = Set.copyOf(u.getRole().getPermissions());
        var scope = CompanyRecordScope.of(perms);
        if (scope == CompanyRecordScope.DENY || (!tenantScope.abacEnabled() && scope != CompanyRecordScope.COMPANY)) return;
        AuthUser principal = new AuthUser(
                u.getId(),
                u.getUsername(),
                u.getEmployee() == null ? null : u.getEmployee().getId(),
                u.getRole().getName(),
                perms, token.tenantId(), token.sessionId());
        List<SimpleGrantedAuthority> authorities = perms.stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
