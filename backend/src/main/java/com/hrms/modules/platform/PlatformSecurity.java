package com.hrms.modules.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.hrms.modules.admin.model.AppUser;
import com.hrms.modules.admin.repository.UserRepo;
import com.hrms.modules.audit.interceptor.AuditCapture;
import com.hrms.shared.security.CompanySessions;
import com.hrms.shared.security.JwtService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@ConditionalOnProperty(name = "app.platform.enabled", havingValue = "true")
public class PlatformSecurity {
    @Bean
    @Order(1)
    SecurityFilterChain platformChain(HttpSecurity http, PlatformAuth auth,
                                      @Value("${app.tenancy.allow-additional-companies:false}") boolean companiesEnabled,
                                      ObjectProvider<AuditCapture> captures,
                                      ObjectProvider<JwtService> jwtProvider,
                                      ObjectProvider<UserRepo> usersProvider,
                                      ObjectProvider<CompanySessions> sessionsProvider)
            throws Exception {
        if (!companiesEnabled) throw new IllegalStateException("Platform requires additional company access");
        var capture = captures.getIfAvailable();
        return http.securityMatcher("/api/platform/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/api/platform/auth/login").permitAll()
                        .anyRequest().access((authentication, context) -> new AuthorizationDecision(
                                authentication.get().getPrincipal() instanceof PlatformAuth.Owner owner
                                        && owner.role().allows(context.getRequest().getMethod(), context.getRequest().getServletPath()))))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                    FilterChain chain) throws IOException, ServletException {
                        String header = request.getHeader("Authorization");
                        PlatformAuth.Owner owner = null;
                        if (header != null && header.startsWith("Bearer ")) {
                            String tokenStr = header.substring(7);
                            owner = auth.authenticate(tokenStr);
                            if (owner == null) {
                                var jwt = jwtProvider.getIfAvailable();
                                var users = usersProvider.getIfAvailable();
                                var sessions = sessionsProvider.getIfAvailable();
                                if (jwt != null && users != null && sessions != null) {
                                    var compToken = jwt.parseCompanyToken(tokenStr);
                                    if (compToken != null && sessions.isActive(compToken)) {
                                        AppUser user = users.findById(compToken.userId()).orElse(null);
                                        if (user != null && user.getRole() != null && user.getRole().getPermissions() != null
                                                && (user.getRole().getPermissions().contains("*") || "Super Admin".equalsIgnoreCase(user.getRole().getName()))) {
                                            owner = new PlatformAuth.Owner(
                                                    UUID.nameUUIDFromBytes(("superadmin-" + user.getId()).getBytes(StandardCharsets.UTF_8)),
                                                    user.getUsername(),
                                                    compToken.sessionId(),
                                                    Instant.now(),
                                                    PlatformRole.GLOBAL_SUPER_ADMIN
                                            );
                                        }
                                    }
                                }
                            }
                            if (owner != null) {
                                var context = SecurityContextHolder.createEmptyContext();
                                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(owner, null, List.of()));
                                SecurityContextHolder.setContext(context);
                            }
                        }
                        if (capture != null) capture.start(request, response);
                        int status = 500;
                        try {
                            chain.doFilter(request, response);
                            status = response.getStatus();
                        } finally {
                            if (capture != null) capture.platform(owner, request, status);
                        }
                    }
                }, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
