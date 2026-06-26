package com.longdq.adaptengbackend.repository;

import com.longdq.adaptengbackend.entity.PaymentTransaction;
import com.longdq.adaptengbackend.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByTransactionCode(String transactionCode);

    List<PaymentTransaction> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, PaymentStatus status);

    List<PaymentTransaction> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime dateTime);

    List<PaymentTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId);
}