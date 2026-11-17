package com.company.hrms.notification.controller;

import com.company.hrms.notification.model.Notification;
import com.company.hrms.notification.repository.NotificationRepo;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.hrms.common.ApiException;
import com.company.hrms.security.CurrentUser;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepo notifications;

    public NotificationController(NotificationRepo notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public Map<String, Object> list() {
        Long userId = CurrentUser.get().id();
        return Map.of(
                "unread", notifications.countByUserIdAndReadFalse(userId),
                "items", notifications.findTop100ByUserIdOrderByCreatedAtDesc(userId));
    }

    @PostMapping("/{id}/read")
    public void read(@PathVariable Long id) {
        Notification n = notifications.findById(id).orElseThrow(() -> ApiException.notFound("Not found"));
        if (!n.getUser().getId().equals(CurrentUser.get().id())) {
            throw ApiException.forbidden("Not your notification");
        }
        n.setRead(true);
        notifications.save(n);
    }

    @PostMapping("/read-all")
    public void readAll() {
        List<Notification> unread = notifications.findByUserIdAndReadFalse(CurrentUser.get().id());
        unread.forEach(n -> n.setRead(true));
        notifications.saveAll(unread);
    }
}
