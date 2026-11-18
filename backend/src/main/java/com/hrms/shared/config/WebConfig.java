package com.hrms.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.hrms.modules.audit.interceptor.AuditInterceptor;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuditInterceptor auditInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditInterceptor).addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/auth/accept-admin-invite", "/api/platform/**");
    }
}
