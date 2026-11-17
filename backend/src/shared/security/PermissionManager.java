package shared.security;

import java.util.Set;
import java.util.function.Supplier;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/**
 * Role-based permission check for every API call.
 *
 * URLs look like /api/{module}/... and each needs the permission "{module}:{action}":
 *   GET                                  -> read
 *   POST .../approve, /reject, /submit   -> approve
 *   any other POST, PUT, DELETE          -> write
 * The modules auth, me and notifications are open to every signed-in user.
 */
@Component
public class PermissionManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final Set<String> OPEN_MODULES = Set.of("auth", "me", "notifications");
    private static final Set<String> APPROVE_VERBS = Set.of("approve", "reject", "submit", "pay");

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        Authentication auth = authentication.get();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) {
            return new AuthorizationDecision(false);
        }
        // "", "api", module, ...
        String[] seg = context.getRequest().getRequestURI().split("/");
        if (seg.length < 3 || OPEN_MODULES.contains(seg[2])) {
            return new AuthorizationDecision(true);
        }
        String module = seg[2];
        String method = context.getRequest().getMethod();
        String action;
        if ("GET".equals(method)) {
            action = "read";
        } else if (APPROVE_VERBS.contains(seg[seg.length - 1])) {
            action = "approve";
        } else {
            action = "write";
        }
        return new AuthorizationDecision(user.can(module + ":" + action));
    }
}
