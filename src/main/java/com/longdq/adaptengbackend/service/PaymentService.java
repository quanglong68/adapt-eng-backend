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
import java.time.format.DateTimeFormatter;
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

        String transactionCode = generateTransactionCode();

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setUser(user);
        transaction.setSubscriptionPackage(subscriptionPackage);
        transaction.setAmount(subscriptionPackage.getPrice());
        transaction.setStatus(PaymentStatus.PENDING);
        transaction.setTransactionCode(transactionCode);
        paymentTransactionRepository.save(transaction);

        // 1. Khởi tạo Map tham số (Gọn gàng, sạch sẽ)
        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", "2.1.0");
        vnpParams.put("vnp_Command", "pay");
        vnpParams.put("vnp_TmnCode", vnpayProperties.getTmnCode()); // Hút từ file yml
        vnpParams.put("vnp_Amount", String.valueOf(subscriptionPackage.getPrice() * 100));
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", transactionCode);

        // Giữ lại bí quyết: Viết liền không dấu cách để chống lỗi URLEncoder
        vnpParams.put("vnp_OrderInfo", "ThanhToanVIP");

        vnpParams.put("vnp_OrderType", "other");
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_ReturnUrl", vnpayProperties.getReturnUrl()); // Hút từ file yml
        vnpParams.put("vnp_IpAddr", "127.0.0.1"); // Vẫn giữ IP để test

        // 2. Ép múi giờ chuẩn GMT+7 của VNPAY
        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        vnpParams.put("vnp_CreateDate", formatter.format(cld.getTime()));
        cld.add(Calendar.MINUTE, 15);
        vnpParams.put("vnp_ExpireDate", formatter.format(cld.getTime()));

        // 3. Giao cho VnpayUtil sinh link và băm chữ ký
        String finalUrl = vnpayUtil.buildPaymentUrl(vnpParams);

        log.info("FINAL URL: " + finalUrl);

        return CreatePaymentUrlResponseDto.builder()
                .vnpayUrl(finalUrl)
                .transactionCode(transactionCode)
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

        if (transaction.getStatus() == PaymentStatus.SUCCESS) {
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

        // 1. Kiểm tra chữ ký bảo mật
        if (!vnpayUtil.validateSignature(params)) {
            response.put("success", false);
            response.put("message", "Chữ ký không hợp lệ, phát hiện nghi vấn giả mạo!");
            return response;
        }

        String transactionCode = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");

        // 2. Lấy giao dịch từ DB
        PaymentTransaction transaction = paymentTransactionRepository.findByTransactionCode(transactionCode)
                .orElseThrow(() -> new ResourceNotFoundException("Giao dịch không tồn tại."));

        // 3. Nếu đơn đã xử lý rồi thì thôi (Chống trùng lặp)
        if (transaction.getStatus() == PaymentStatus.SUCCESS) {
            response.put("success", true);
            response.put("message", "Giao dịch đã được ghi nhận thành công từ trước.");
            return response;
        }

        // 4. Nếu VNPAY báo thành công (00) -> Cấp VIP
        if ("00".equals(responseCode)) {
            transaction.setStatus(PaymentStatus.SUCCESS);
            paymentTransactionRepository.save(transaction);

            activateOrExtendSubscription(transaction); // Tái sử dụng hàm cấp thẻ VIP có sẵn

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
    private String generateTransactionCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
