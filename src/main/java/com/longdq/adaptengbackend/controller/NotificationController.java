package com.longdq.adaptengbackend.controller;

import com.longdq.adaptengbackend.entity.Notification;
import com.longdq.adaptengbackend.entity.User;
import com.longdq.adaptengbackend.service.NotificationService;
import com.longdq.adaptengbackend.util.SecurityUtils;
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

    // ==============================================================
    // 🚀 DEV TOOLS: ENDPOINT DÙNG ĐỂ TEST BẮN THÔNG BÁO TỪ POSTMAN
    // ==============================================================
    @PostMapping("/test-send")
    public ResponseEntity<String> testSendNotification(@RequestBody TestNotificationRequest request) {
        User user = SecurityUtils.getCurrentUser();

        Notification notification = Notification.builder()
                .userId(user.getId())
                .title(request.getTitle())
                .message(request.getMessage())
                .type(com.longdq.adaptengbackend.enums.NotificationType.valueOf(request.getType()))
                .actionUrl(request.getActionUrl())
                .isRead(false)
                .build();

        // Gọi service bắn thông báo qua đường ống SSE
        notificationService.sendNotification(user.getId(), notification);

        return ResponseEntity.ok("Đã bắn thông báo thành công qua SSE!");
    }

    // DTO tĩnh dùng tạm để hứng data test từ Postman
    @lombok.Data
    public static class TestNotificationRequest {
        private String title;
        private String message;
        private String type;
        private String actionUrl;
    }
}