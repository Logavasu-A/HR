package shared.security;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import shared.tenancy.TenantDatabaseScope;
import modules.admin.interceptor.AuditInterceptor;
import modules.admin.interceptor.AuditCapture;
import modules.platform.OwnerWorkspaceFilter;
import modules.platform.OwnerWorkspaces;

import modules.admin.repository.UserRepo;

@Configuration
public class SecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwt, UserRepo users,
                                           PermissionManager permissionManager, CompanySessions sessions,
                                           TenantDatabaseScope tenantScope, AuditInterceptor audit,
                                           ObjectProvider<OwnerWorkspaces> ownerWorkspaces, ObjectMapper json,
                                           ObjectProvider<FinanceControls> finance,
                                           ObjectProvider<AuditCapture> capture) throws Exception {
        http
                .csrf(c -> c.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/login", "/api/auth/accept-admin-invite").permitAll()
                        .requestMatchers("/api/platform/**").denyAll()
                        .requestMatchers("/api/**").access(permissionManager)
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new JwtFilter(jwt, users, sessions, tenantScope, audit, capture.getIfAvailable()),
                        UsernamePasswordAuthenticationFilter.class);
        var controls = finance.getIfAvailable();
        ownerWorkspaces.ifAvailable(workspaces -> http.addFilterBefore(
                new OwnerWorkspaceFilter(workspaces, audit, json, controls != null), JwtFilter.class));
        if (controls != null) http.addFilterAfter(new FinanceFilter(controls, json, audit), AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") List<String> allowedOrigins) {
        if (allowedOrigins.contains("*")) {
            throw new IllegalArgumentException("Configure explicit HRMS frontend origins");
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
