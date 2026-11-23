package modules.notifications.controller;

import modules.notifications.model.Notification;
import modules.notifications.repository.NotificationRepo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import shared.common.ApiException;
import shared.common.PageResponse;
import shared.security.CurrentUser;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepo notifications;

    public NotificationController(NotificationRepo notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) Integer page,
                                    @RequestParam(defaultValue = "20") int size) {
        Long userId = CurrentUser.get().id();
        long unread = notifications.countByUserIdAndReadFalse(userId);
        if (page != null) {
            Page<Notification> p = notifications.findByUserIdOrderByCreatedAtDesc(
                    userId, PageResponse.pageable(page, size));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("unread", unread);
            out.put("items", p.getContent());
            out.put("page", p.getNumber());
            out.put("size", p.getSize());
            out.put("totalElements", p.getTotalElements());
            out.put("totalPages", p.getTotalPages());
            out.put("first", p.isFirst());
            out.put("last", p.isLast());
            return out;
        }
        return Map.of(
                "unread", unread,
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
