package com.hrms.modules.platform;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
@Profile("local-isolation")
@ConditionalOnProperty(name = "app.platform.admin-view.enabled", havingValue = "true")
@EnableJpaAuditing(auditorAwareRef = "workspaceOwnerAuditor", setDates = false)
public class OwnerWorkspaceAuditConfig {
    @Bean
    AuditorAware<String> workspaceOwnerAuditor() {
        return () -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof OwnerWorkspacePrincipal owner) {
                return Optional.of(owner.owner().username());
            }
            return Optional.empty();
        };
    }
}
