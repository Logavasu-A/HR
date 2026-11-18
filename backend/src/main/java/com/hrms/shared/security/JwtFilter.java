package com.hrms.shared.security;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.hrms.modules.admin.model.AppUser;
import com.hrms.modules.admin.repository.UserRepo;
import com.hrms.shared.tenancy.CompanyTenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Reads the "Authorization: Bearer ..." header and signs the user in for this request. */
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final UserRepo users;
    private final CompanySessions sessions;

    public JwtFilter(JwtService jwt, UserRepo users, CompanySessions sessions) {
        this.jwt = jwt;
        this.users = users;
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (!"/api/auth/login".equals(request.getServletPath()) && header != null && header.startsWith("Bearer ")) {
            JwtService.CompanyToken token = jwt.parseCompanyToken(header.substring(7));
            if (token != null && sessions.isActive(token)) {
                CompanyTenancy.attach(token.tenantId());
                try {
                    users.findById(token.userId()).filter(AppUser::isActive).ifPresent(user -> signIn(user, token));
                    chain.doFilter(request, response);
                } finally {
                    CompanyTenancy.clear();
                }
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void signIn(AppUser u, JwtService.CompanyToken token) {
        Set<String> perms = Set.copyOf(u.getRole().getPermissions());
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
