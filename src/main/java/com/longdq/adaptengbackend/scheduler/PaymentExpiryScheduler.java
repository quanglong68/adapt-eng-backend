package com.longdq.adaptengbackend.scheduler;

import com.longdq.adaptengbackend.entity.PaymentTransaction;
import com.longdq.adaptengbackend.enums.PaymentStatus;
import com.longdq.adaptengbackend.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentExpiryScheduler {

    private final PaymentTransactionRepository paymentTransactionRepository;

    /**
     * Chạy mỗi 1 phút: Quét các transaction PENDING quá 15 phút -> chuyển thành CANCELED
     */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void cancelExpiredPendingTransactions() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(15);
        List<PaymentTransaction> expiredTransactions = paymentTransactionRepository
                .findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, threshold);

        if (expiredTransactions.isEmpty()) {
            return;
        }

        log.info("Found {} expired PENDING transactions to cancel", expiredTransactions.size());
        for (PaymentTransaction transaction : expiredTransactions) {
            transaction.setStatus(PaymentStatus.CANCELED);
            paymentTransactionRepository.save(transaction);
            log.info("Auto-canceled expired transaction: {}", transaction.getTransactionCode());
        }
    }
}