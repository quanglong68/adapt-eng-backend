package com.longdq.adaptengbackend.modules.premium.dto;

/**
 * Lý do chưa có đề khi status = EMPTY.
 */
public enum VipEntertainmentEmptyReason {
    /** Không VIP ACTIVE (trường hợp này thường đã bị chặn 403 ở guard, giữ để đủ nghĩa). */
    NOT_VIP,
    /** Chưa lưu từ PENDING nào nên job 2h sáng không có gì để sinh đề. */
    NO_PENDING_WORDS,
    /** Đã lưu từ, đang chờ job 2h sáng sinh đề (hoặc AI lỗi đêm qua, job đêm nay retry). */
    WAITING_JOB
}
