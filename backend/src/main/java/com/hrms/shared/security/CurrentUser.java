package com.hrms.shared.security;

import java.util.UUID;

import com.hrms.modules.platform.OwnerWorkspacePrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.hrms.shared.common.ApiException;

/** Access to the signed-in user from anywhere in the code. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthUser get() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof AuthUser u) {
            return u;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "Not signed in");
    }

    public static String usernameOrSystem() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof AuthUser u) {
            return u.username();
        }
        if (a != null && a.getPrincipal() instanceof OwnerWorkspacePrincipal owner) {
            return owner.owner().username();
        }
        return "system";
    }

    public static UUID tenantId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OwnerWorkspacePrincipal owner) {
            return owner.tenantId();
        }
        return get().tenantId();
    }

    public static boolean hasExplicitPermission(String permission) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OwnerWorkspacePrincipal owner) {
            return owner.allowsBankDetails(permission);
        }
        var user = get();
        return CompanyRecordScope.of(user.permissions()) == CompanyRecordScope.COMPANY
                && user.permissions().contains(permission);
    }

    public static boolean isCompanyUser(Long id) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthUser user && id.equals(user.id());
    }

    public static boolean isPlatformWorkspace() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof OwnerWorkspacePrincipal;
    }

    public static void rejectScopedSelfApproval(Long employeeId) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser user &&
                CompanyRecordScope.of(user.permissions()) != CompanyRecordScope.COMPANY &&
                employeeId.equals(user.employeeId())) {
            throw ApiException.forbidden("You cannot approve or reject your own request.");
        }
    }

    /** The employee record linked to the signed-in user. */
    public static Long employeeId() {
        Long id = get().employeeId();
        if (id == null) {
            throw ApiException.badRequest("Your login is not linked to an employee record");
        }
        return id;
    }
}
