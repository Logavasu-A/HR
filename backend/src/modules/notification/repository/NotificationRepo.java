package modules.notification.repository;

import modules.notification.model.Notification;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepo extends JpaRepository<Notification, Long> {
    List<Notification> findTop100ByUserIdOrderByCreatedAtDesc(Long userId);

    List<Notification> findByUserIdAndReadFalse(Long userId);

    long countByUserIdAndReadFalse(Long userId);
}
