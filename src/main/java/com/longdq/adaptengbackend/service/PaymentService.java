package com.longdq.adaptengbackend.service;

import com.longdq.adaptengbackend.config.VnpayProperties;
import com.longdq.adaptengbackend.dto.CreatePaymentUrlRequestDto;
import com.longdq.adaptengbackend.dto.CreatePaymentUrlResponseDto;
import com.longdq.adaptengbackend.entity.PaymentTransaction;
import com.longdq.adaptengbackend.entity.SubscriptionPackage;
import com.longdq.adaptengbackend.entity.User;
import com.longdq.adaptengbackend.entity.UserSubscription;
import com.longdq.adaptengbackend.enums.PaymentStatus;
import com.longdq.adaptengbackend.enums.SubscriptionStatus;
import com.longdq.adaptengbackend.exception.ResourceNotFoundException;
import com.longdq.adaptengbackend.repository.PaymentTransactionRepository;
import com.longdq.adaptengbackend.repository.SubscriptionPackageRepository;
import com.longdq.adaptengbackend.repository.UserSubscriptionRepository;
import com.longdq.adaptengbackend.util.SecurityUtils;
import com.longdq.adaptengbackend.util.VnpayUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final SubscriptionPackageRepository subscriptionPackageRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final VnpayProperties vnpayProperties;
    private final VnpayUtil vnpayUtil;

    @Transactional
    public CreatePaymentUrlResponseDto createPaymentUrl(CreatePaymentUrlRequestDto request) {
        User user = SecurityUtils.getCurrentUser();
        SubscriptionPackage subscriptionPackage = subscriptionPackageRepository.findById(request.getPackageId())
                .orElseThrow(() -> new ResourceNotFoundException("Gói đăng ký không tồn tại."));

        // Check if user already has active VIP
        boolean hasActiveVip = userSubscriptionRepository
                .existsByUserIdAndStatusAndEndDateGreaterThan(
                        user.getId(),
                        SubscriptionStatus.ACTIVE,
                        LocalDateTime.now()
                );

        String transactionCode = generateTransactionCode();

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setUser(user);
        transaction.setSubscriptionPackage(subscriptionPackage);
        transaction.setAmount(subscriptionPackage.getPrice());
        transaction.setStatus(PaymentStatus.PENDING);
        transaction.setTransactionCode(transactionCode);
        transaction.setCreatedAt(LocalDateTime.now());

        // 1. Khởi tạo Map tham số (Gọn gàng, sạch sẽ)
        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", "2.1.0");
        vnpParams.put("vnp_Command", "pay");
        vnpParams.put("vnp_TmnCode", vnpayProperties.getTmnCode());
        vnpParams.put("vnp_Amount", String.valueOf(subscriptionPackage.getPrice() * 100));
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", transactionCode);
        vnpParams.put("vnp_OrderInfo", "ThanhToanVIP");
        vnpParams.put("vnp_OrderType", "other");
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_ReturnUrl", vnpayProperties.getReturnUrl());
        vnpParams.put("vnp_IpAddr", "127.0.0.1");

        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        vnpParams.put("vnp_CreateDate", formatter.format(cld.getTime()));
        cld.add(Calendar.MINUTE, 15);
        vnpParams.put("vnp_ExpireDate", formatter.format(cld.getTime()));

        String finalUrl = vnpayUtil.buildPaymentUrl(vnpParams);

        // Save vnpayUrl to transaction
        transaction.setVnpayUrl(finalUrl);
        paymentTransactionRepository.save(transaction);

        log.info("FINAL URL: " + finalUrl);

        String warningMessage = null;
        if (hasActiveVip) {
            warningMessage = "Bạn đã có gói VIP đang hoạt động. Thanh toán gói mới sẽ cộng dồn thời gian VIP hiện tại.";
        }

        return CreatePaymentUrlResponseDto.builder()
                .vnpayUrl(finalUrl)
                .transactionCode(transactionCode)
                .hasActiveVip(hasActiveVip)
                .warningMessage(warningMessage)
                .build();
    }

    @Transactional
    public Map<String, String> handleVnpayIpn(Map<String, String> params) {
        Map<String, String> response = new HashMap<>();

        if (!vnpayUtil.validateSignature(params)) {
            log.warn("VNPAY IPN signature validation failed for txnRef: {}", params.get("vnp_TxnRef"));
            response.put("RspCode", "97");
            response.put("Message", "Invalid signature");
            return response;
        }

        String transactionCode = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");

        PaymentTransaction transaction = paymentTransactionRepository.findByTransactionCode(transactionCode)
                .orElseThrow(() -> new ResourceNotFoundException("Giao dịch không tồn tại."));

        if (transaction.getStatus() == PaymentStatus.SUCCESS || transaction.getStatus() == PaymentStatus.CANCELED) {
            response.put("RspCode", "00");
            response.put("Message", "Confirm Success");
            return response;
        }

        if (!"00".equals(responseCode)) {
            transaction.setStatus(PaymentStatus.FAILED);
            paymentTransactionRepository.save(transaction);
            log.warn("VNPAY payment failed for transaction: {}, responseCode: {}", transactionCode, responseCode);
            response.put("RspCode", "00");
            response.put("Message", "Confirm Success");
            return response;
        }

        transaction.setStatus(PaymentStatus.SUCCESS);
        paymentTransactionRepository.save(transaction);

        activateOrExtendSubscription(transaction);

        log.info("VNPAY IPN processed successfully for transaction: {}", transactionCode);
        response.put("RspCode", "00");
        response.put("Message", "Confirm Success");
        return response;
    }

    private void activateOrExtendSubscription(PaymentTransaction transaction) {
        User user = transaction.getUser();
        SubscriptionPackage subscriptionPackage = transaction.getSubscriptionPackage();
        LocalDateTime now = LocalDateTime.now();

        Optional<UserSubscription> existingActive = userSubscriptionRepository
                .findFirstByUserIdAndStatusAndEndDateGreaterThanOrderByEndDateDesc(
                        user.getId(),
                        SubscriptionStatus.ACTIVE,
                        now
                );

        UserSubscription subscription;
        if (existingActive.isPresent()) {
            subscription = existingActive.get();
            subscription.setEndDate(subscription.getEndDate().plusDays(subscriptionPackage.getDurationDays()));
            subscription.setSubscriptionPackage(subscriptionPackage);
        } else {
            subscription = new UserSubscription();
            subscription.setUser(user);
            subscription.setSubscriptionPackage(subscriptionPackage);
            subscription.setStartDate(now);
            subscription.setEndDate(now.plusDays(subscriptionPackage.getDurationDays()));
            subscription.setStatus(SubscriptionStatus.ACTIVE);
        }

        if (subscription.getEndDate().isBefore(now) || subscription.getEndDate().isEqual(now)) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
        } else {
            subscription.setStatus(SubscriptionStatus.ACTIVE);
        }

        userSubscriptionRepository.save(subscription);
    }

    @Transactional
    public Map<String, Object> handleVnpayReturn(Map<String, String> params) {
        Map<String, Object> response = new HashMap<>();

        if (!vnpayUtil.validateSignature(params)) {
            response.put("success", false);
            response.put("message", "Chữ ký không hợp lệ, phát hiện nghi vấn giả mạo!");
            return response;
        }

        String transactionCode = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");

        PaymentTransaction transaction = paymentTransactionRepository.findByTransactionCode(transactionCode)
                .orElseThrow(() -> new ResourceNotFoundException("Giao dịch không tồn tại."));

        if (transaction.getStatus() == PaymentStatus.SUCCESS) {
            response.put("success", true);
            response.put("message", "Giao dịch đã được ghi nhận thành công từ trước.");
            return response;
        }

        if (transaction.getStatus() == PaymentStatus.CANCELED) {
            response.put("success", false);
            response.put("message", "Giao dịch đã bị hủy.");
            return response;
        }

        if ("00".equals(responseCode)) {
            transaction.setStatus(PaymentStatus.SUCCESS);
            paymentTransactionRepository.save(transaction);
            activateOrExtendSubscription(transaction);
            response.put("success", true);
            response.put("message", "Thanh toán thành công. Đã nâng cấp VIP!");
        } else {
            transaction.setStatus(PaymentStatus.FAILED);
            paymentTransactionRepository.save(transaction);
            response.put("success", false);
            response.put("message", "Giao dịch thất bại hoặc đã bị hủy.");
        }

        return response;
    }

    @Transactional
    public void cancelTransaction(Long transactionId) {
        User user = SecurityUtils.getCurrentUser();
        PaymentTransaction transaction = paymentTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Giao dịch không tồn tại."));

        if (!transaction.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Bạn không có quyền hủy giao dịch này.");
        }

        if (transaction.getStatus() != PaymentStatus.PENDING) {
            throw new RuntimeException("Chỉ có thể hủy giao dịch đang chờ thanh toán.");
        }

        // Check if 15 minutes have passed
        if (transaction.getCreatedAt() != null &&
                transaction.getCreatedAt().plusMinutes(15).isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Giao dịch đã quá thời gian hủy (15 phút).");
        }

        transaction.setStatus(PaymentStatus.CANCELED);
        paymentTransactionRepository.save(transaction);
        log.info("Transaction {} canceled by user {}", transaction.getTransactionCode(), user.getEmail());
    }

    public List<Map<String, Object>> getTransactionHistory() {
        User user = SecurityUtils.getCurrentUser();
        List<PaymentTransaction> transactions = paymentTransactionRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        List<Map<String, Object>> result = new ArrayList<>();
        for (PaymentTransaction t : transactions) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", t.getId());
            item.put("transactionCode", t.getTransactionCode());
            item.put("amount", t.getAmount());
            item.put("status", t.getStatus().name());
            item.put("createdAt", t.getCreatedAt());
            item.put("vnpayUrl", t.getVnpayUrl());
            item.put("packageName", t.getSubscriptionPackage() != null ? t.getSubscriptionPackage().getName() : null);
            result.add(item);
        }
        return result;
    }

    private String generateTransactionCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}