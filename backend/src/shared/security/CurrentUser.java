package shared.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import shared.common.ApiException;

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
        return "system";
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
