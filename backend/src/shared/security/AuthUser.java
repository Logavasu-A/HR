package com.company.hrms.security;

import java.util.Set;

/** The signed-in user, as stored in the security context for each request. */
public record AuthUser(Long id, String username, Long employeeId, String role, Set<String> permissions) {

    public boolean can(String permission) {
        if (permissions.contains("*") || permissions.contains(permission)) {
            return true;
        }
        int colon = permission.indexOf(':');
        return colon > 0 && permissions.contains(permission.substring(0, colon) + ":*");
    }
}
