package com.hrms.shared.tenancy;

import java.util.Map;
import java.util.UUID;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

@Component
public class CompanyTenancy implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {
    private static final UUID NO_TENANT = new UUID(0, 0);
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    public static void attach(UUID id) {
        if (CURRENT.get() != null) throw new IllegalStateException("Company context is already bound");
        CURRENT.set(id);
    }

    public static void clear() {
        CURRENT.remove();
    }

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        UUID company = CURRENT.get();
        return company == null ? NO_TENANT : company;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    @Override
    public void customize(Map<String, Object> properties) {
        properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
