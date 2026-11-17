package modules.notification.service;

import modules.notification.model.Notification;
import modules.notification.repository.NotificationRepo;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import modules.admin.model.AppUser;
import modules.admin.repository.UserRepo;
import modules.employee.model.Employee;

/** In-app notifications, plus an email copy when app.mail.enabled is true. */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepo notifications;
    private final UserRepo users;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final boolean mailEnabled;
    private final String mailFrom;

    public NotificationService(NotificationRepo notifications, UserRepo users,
                               ObjectProvider<JavaMailSender> mailSender,
                               @Value("${app.mail.enabled}") boolean mailEnabled,
                               @Value("${app.mail.from}") String mailFrom) {
        this.notifications = notifications;
        this.users = users;
        this.mailSender = mailSender;
        this.mailEnabled = mailEnabled;
        this.mailFrom = mailFrom;
    }

    public void notifyUser(AppUser user, String title, String message) {
        Notification n = new Notification();
        n.setUser(user);
        n.setTitle(title);
        n.setMessage(message);
        n.setCreatedAt(LocalDateTime.now());
        notifications.save(n);
        sendEmail(user.getEmail(), title, message);
    }

    /** Notifies the login linked to this employee; falls back to email only when there is no login. */
    public void notifyEmployee(Employee employee, String title, String message) {
        if (employee == null || employee.getId() == null) {
            return;
        }
        users.findFirstByEmployeeId(employee.getId()).ifPresentOrElse(
                u -> notifyUser(u, title, message),
                () -> sendEmail(employee.getEmail(), title, message));
    }

    /** Notifies every active user whose role grants the permission, e.g. "leave:approve". */
    public void notifyPermission(String permission, String title, String message) {
        String module = permission.substring(0, permission.indexOf(':'));
        users.findAll().stream()
                .filter(AppUser::isActive)
                .filter(u -> {
                    var perms = u.getRole().getPermissions();
                    return perms.contains("*") || perms.contains(permission) || perms.contains(module + ":*");
                })
                .forEach(u -> notifyUser(u, title, message));
    }

    private void sendEmail(String to, String subject, String body) {
        if (!mailEnabled || to == null || to.isBlank()) {
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("app.mail.enabled is true but JavaMailSender is not configured");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(mailFrom);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            sender.send(msg);
        } catch (RuntimeException e) {
            log.warn("Could not send email to {}: {}", to, e.getMessage());
        }
    }
}
