package com.hrms.modules.platform;

import java.util.Set;
import java.util.UUID;

/** The authenticated platform owner acting within one server-authorized company. */
public record OwnerWorkspacePrincipal(PlatformAuth.Owner owner, UUID tenantId) {
    private static final Set<String> MODULES = Set.of("dashboard", "core", "attendance", "leave",
            "payroll", "recruitment", "expense", "performance", "admin", "sensitive");

    public static boolean supports(String module) {
        return MODULES.contains(module);
    }

    public boolean allowsBankDetails(String permission) {
        return "employee-bank:read".equals(permission) || "employee-bank:write".equals(permission);
    }
}
