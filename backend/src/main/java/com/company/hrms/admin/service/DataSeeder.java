package com.company.hrms.admin.service;

import com.company.hrms.admin.model.AppUser;
import com.company.hrms.admin.model.Role;
import com.company.hrms.admin.repository.RoleRepo;
import com.company.hrms.admin.repository.UserRepo;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


/**
 * Initializes essential security roles and default admin if missing from the DB.
 * Does not seed any mock, sample, or starter data.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final RoleRepo roles;
    private final UserRepo users;
    private final PasswordEncoder encoder;
    private final String adminPassword;

    public DataSeeder(RoleRepo roles, UserRepo users, PasswordEncoder encoder,
                      @Value("${app.admin.initial-password}") String adminPassword) {
        this.roles = roles;
        this.users = users;
        this.encoder = encoder;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (roles.count() == 0) {
            role("ADMIN", "Full access to everything", Set.of("*"));
            role("HR_MANAGER", "Runs all HR modules", Set.of(
                    "core:*", "leave:*", "attendance:*", "payroll:*", "recruitment:*", "expense:*",
                    "performance:*", "dashboard:read"));
            role("MANAGER", "Approves leave and expenses, reviews performance", Set.of(
                    "core:read", "leave:read", "leave:approve", "attendance:read", "expense:read", "expense:approve",
                    "performance:read", "performance:write", "recruitment:read", "dashboard:read"));
            role("EMPLOYEE", "Self-service only", Set.of());
        }

        if (users.count() == 0) {
            if (adminPassword != null && !adminPassword.isBlank()) {
                AppUser admin = new AppUser();
                admin.setUsername("admin");
                admin.setPasswordHash(encoder.encode(adminPassword));
                admin.setRole(roles.findByName("ADMIN").orElseThrow());
                admin.setActive(true);
                users.save(admin);
                log.info("Created the initial admin user from ADMIN_INITIAL_PASSWORD.");
            }
        }
    }

    private void role(String name, String description, Set<String> permissions) {
        Role r = new Role();
        r.setName(name);
        r.setDescription(description);
        r.getPermissions().addAll(permissions);
        roles.save(r);
    }
}
