package com.longdq.adaptengbackend.modules.notification.service;

import com.longdq.adaptengbackend.modules.notification.entity.Notification;
import com.longdq.adaptengbackend.modules.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import com.longdq.adaptengbackend.modules.user.entity.User;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    // Lưu trữ các kết nối SSE của user. Dùng CopyOnWriteArrayList để hỗ trợ user mở nhiều tab cùng lúc
    private final Map<UUID, List<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    // 1. CLIENT GỌI HÀM NÀY ĐỂ KẾT NỐI ĐƯỜNG ỐNG SSE
    public SseEmitter subscribe(UUID userId) {
        // Tạo Emitter với thời gian timeout rất dài (1 tiếng)
        SseEmitter emitter = new SseEmitter(60 * 60 * 1000L);

        userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // Xử lý khi đường ống đứt (tắt trình duyệt, rớt mạng)
        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError((e) -> removeEmitter(userId, emitter));

        // Gửi một tin nhắn chào hỏi để trình duyệt biết đường ống đã thông
        try {
            emitter.send(SseEmitter.event().name("INIT").data("Connected to Notification SSE"));
        } catch (IOException e) {
            removeEmitter(userId, emitter);
        }

        return emitter;
    }

    private void removeEmitter(UUID userId, SseEmitter emitter) {
        List<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                userEmitters.remove(userId);
            }
        }
    }

    // 2. HỆ THỐNG GỌI HÀM NÀY ĐỂ BẮN THÔNG BÁO CHO USER
    @Transactional
    public void sendNotification(UUID userId, Notification notification) {
        // Lưu vào Database trước để không bị mất data
        Notification savedNotification = notificationRepository.save(notification);

        // Bắn trực tiếp qua các ống SSE đang mở của User (Nhanh như chớp)
        List<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters != null) {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("NEW_NOTIFICATION")
                            .data(savedNotification));
                } catch (IOException e) {
                    emitter.complete();
                    removeEmitter(userId, emitter);
                }
            }
        }
    }

    // CÁC HÀM GET LỊCH SỬ THÔNG BÁO
    public List<Notification> getUserNotifications(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAsRead(UUID notificationId) {
        notificationRepository.findById(notificationId).ifPresent(notif -> {
            notif.setRead(true);
            notificationRepository.save(notif);
        });
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        List<Notification> unreadNotifs = notificationRepository.findByUserIdAndIsReadFalse(userId);
        unreadNotifs.forEach(notif -> notif.setRead(true));
        notificationRepository.saveAll(unreadNotifs);
    }
}