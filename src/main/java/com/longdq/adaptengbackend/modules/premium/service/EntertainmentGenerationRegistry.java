package com.longdq.adaptengbackend.modules.premium.service;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chốt chặn chống spam nút "Tạo đề ngay": mỗi user chỉ có 1 tiến trình
 * sinh đề giải trí chạy tại 1 thời điểm. Mất khi restart BE (chấp nhận được
 * vì check DB quota + đề dở vẫn chặn được hầu hết trường hợp trùng).
 */
@Component
public class EntertainmentGenerationRegistry {

    private final Set<UUID> generating = ConcurrentHashMap.newKeySet();

    /** @return true nếu chiếm chốt thành công, false nếu đang có tiến trình chạy */
    public boolean tryAcquire(UUID userId) {
        return generating.add(userId);
    }

    public void release(UUID userId) {
        generating.remove(userId);
    }
}
