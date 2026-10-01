package com.longdq.adaptengbackend.modules.premium.dto;

/**
 * Trạng thái đề giải trí VIP trả về cho FE (thay cho việc đoán qua isCompleted/contentJson).
 */
public enum VipEntertainmentStatus {
    /** Có đề chưa làm (kèm contentJson + entertainmentDate để biết đề ngày nào). */
    HAS_STORY,
    /** Hôm nay đã làm xong 1 đề (bản ghi completed có entertainmentDate = hôm nay). */
    DONE_TODAY,
    /** Chưa có đề nào để làm (xem emptyReason). */
    EMPTY
}
