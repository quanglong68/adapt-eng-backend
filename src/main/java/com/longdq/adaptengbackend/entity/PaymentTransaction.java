package com.longdq.adaptengbackend.entity;

import com.longdq.adaptengbackend.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_package_id")
    private SubscriptionPackage subscriptionPackage;

    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PaymentStatus status;

    @Column(name = "transaction_code", unique = true)
    private String transactionCode;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "vnpay_url", columnDefinition = "TEXT")
    private String vnpayUrl;
}