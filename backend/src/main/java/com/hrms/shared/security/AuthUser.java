package com.hrms.shared.security;

import java.util.Set;
import java.util.UUID;

/** The signed-in user, as stored in the security context for each request. */
public record AuthUser(Long id, String username, Long employeeId, String role, Set<String> permissions,
                       UUID tenantId, UUID sessionId) {

    public boolean can(String permission) {
        return grants(permissions, permission);
    }

    public static boolean grants(Set<String> permissions, String permission) {
        if (permissions.contains("*") || permissions.contains(permission)) {
            return true;
        }
        int colon = permission.indexOf(':');
        return colon > 0 && permissions.contains(permission.substring(0, colon) + ":*");
    }
}
