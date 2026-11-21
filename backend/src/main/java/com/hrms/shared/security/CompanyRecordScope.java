package com.hrms.shared.security;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.hrms.shared.common.ApiException;

public enum CompanyRecordScope {
    COMPANY, DEPARTMENT, DIRECT_REPORTS, SELF, DENY;

    public static final List<String> MARKERS = List.of("scope:department", "scope:direct-reports", "scope:self");
    private static final Set<String> TEAM_READS = Set.of("core", "attendance", "leave", "expense", "performance");
    private static final Set<String> TEAM_APPROVALS = Set.of("leave", "expense");

    public static CompanyRecordScope of(Set<String> permissions) {
        var markers = permissions.stream().filter(permission -> permission.startsWith("scope:")).toList();
        if (markers.isEmpty()) return COMPANY;
        if (markers.size() != 1) return DENY;
        return switch (markers.getFirst()) {
            case "scope:department" -> DEPARTMENT;
            case "scope:direct-reports" -> DIRECT_REPORTS;
            case "scope:self" -> SELF;
            default -> DENY;
        };
    }

    public boolean allows(String module, String action) {
        return this == COMPANY || ((this == DEPARTMENT || this == DIRECT_REPORTS) &&
                (("read".equals(action) && TEAM_READS.contains(module)) ||
                 ("approve".equals(action) && TEAM_APPROVALS.contains(module))));
    }

    public Set<String> displayPermissions(Set<String> permissions) {
        if (this == COMPANY) return permissions;
        return TEAM_READS.stream().flatMap(module -> java.util.stream.Stream.of("read", "approve")
                .filter(action -> allows(module, action)).map(action -> module + ":" + action))
                .filter(permission -> AuthUser.grants(permissions, permission)).collect(Collectors.toUnmodifiableSet());
    }

    public static void checkDelegation(Set<String> permissions, boolean abacEnabled) {
        if (permissions == null) throw ApiException.badRequest("Permissions must be supplied as a set.");
        var scope = of(permissions);
        if (scope == DENY || (!abacEnabled && scope != COMPANY)) {
            throw ApiException.badRequest("Choose at most one supported record scope.");
        }
        if (!abacEnabled || CurrentUser.isPlatformWorkspace()) return;
        var actor = CurrentUser.get();
        if (permissions.stream().anyMatch(permission ->
                !permission.startsWith("scope:") && !actor.can(permission))) {
            throw ApiException.forbidden("You cannot administer permissions you do not hold.");
        }
    }
}
