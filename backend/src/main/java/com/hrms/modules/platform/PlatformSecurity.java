package com.hrms.modules.platform;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
                                      @Value("${app.tenancy.allow-additional-companies:false}") boolean companiesEnabled)
            throws Exception {
        if (!companiesEnabled) throw new IllegalStateException("Platform requires additional company access");
        return http.securityMatcher("/api/platform/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/api/platform/auth/login").permitAll()
                        .anyRequest().access((authentication, context) -> new AuthorizationDecision(
                                authentication.get().getPrincipal() instanceof PlatformAuth.Owner)))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                    FilterChain chain) throws IOException, ServletException {
                        String header = request.getHeader("Authorization");
                        if (header != null && header.startsWith("Bearer ")) {
                            var owner = auth.authenticate(header.substring(7));
                            if (owner != null) {
                                var context = SecurityContextHolder.createEmptyContext();
                                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(owner, null, List.of()));
                                SecurityContextHolder.setContext(context);
                            }
                        }
                        chain.doFilter(request, response);
                    }
                }, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
