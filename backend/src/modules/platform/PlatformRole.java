package modules.platform;

public enum PlatformRole {
    GLOBAL_SUPER_ADMIN, PLATFORM_ADMINISTRATOR, SECURITY_ADMINISTRATOR;

    boolean allows(String method, String path) {
        if (this == GLOBAL_SUPER_ADMIN) return true;
        if (("GET".equals(method) && "/api/platform/session".equals(path))
                || ("POST".equals(method) && "/api/platform/logout".equals(path))) return true;
        if (this == SECURITY_ADMINISTRATOR) {
            return ("GET".equals(method) && ("/api/platform/activity".equals(path) || "/api/platform/access".equals(path)))
                    || ("PUT".equals(method) && path.matches("/api/platform/access/[0-9a-fA-F-]{36}"));
        }
        if ("GET".equals(method) && "/api/platform/dashboard".equals(path)) return true;
        if ("/api/platform/companies".equals(path)) return "GET".equals(method) || "POST".equals(method);
        String company = "/api/platform/companies/[0-9a-fA-F-]{36}";
        return (("GET".equals(method) || "PUT".equals(method)) && path.matches(company))
                || ("POST".equals(method) && path.matches(company + "/(suspend|reactivate|invitation/(resend|revoke))"));
    }
}
