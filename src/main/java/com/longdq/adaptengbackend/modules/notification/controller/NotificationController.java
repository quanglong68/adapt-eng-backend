package com.longdq.adaptengbackend.modules.notification.controller;

import com.longdq.adaptengbackend.modules.notification.entity.Notification;
import com.longdq.adaptengbackend.modules.user.entity.User;
import com.longdq.adaptengbackend.modules.notification.service.NotificationService;
import com.longdq.adaptengbackend.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Fix lỗi CORS muôn thuở
public class NotificationController {

    private final NotificationService notificationService;

    // 1. ENDPOINT KẾT NỐI ĐƯỜNG ỐNG (Content-Type phải là text/event-stream)
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNotifications() {
        User user = SecurityUtils.getCurrentUser();
        return notificationService.subscribe(user.getId());
    }

    // 2. LẤY DANH SÁCH & SỐ LƯỢNG CHƯA ĐỌC
    @GetMapping
    public ResponseEntity<?> getNotifications() {
        User user = SecurityUtils.getCurrentUser();
        List<Notification> history = notificationService.getUserNotifications(user.getId());
        long unreadCount = notificationService.getUnreadCount(user.getId());

        return ResponseEntity.ok(Map.of(
                "unreadCount", unreadCount,
                "notifications", history
        ));
    }

    // 3. ĐÁNH DẤU ĐÃ ĐỌC
    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }


    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead() {
        User user = SecurityUtils.getCurrentUser();
        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.ok().build();
    }
}