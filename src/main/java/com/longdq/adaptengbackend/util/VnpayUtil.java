package com.longdq.adaptengbackend.util;

import com.longdq.adaptengbackend.config.VnpayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
@RequiredArgsConstructor
public class VnpayUtil {

    private final VnpayProperties vnpayProperties;

    // Xây dựng URL thanh toán (Bê nguyên logic chuẩn từ VNPayService cũ)
    public String buildPaymentUrl(Map<String, String> params) {
        List<String> fieldNames = new ArrayList<>(params.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        try {
            Iterator<String> itr = fieldNames.iterator();
            while (itr.hasNext()) {
                String fieldName = itr.next();
                String fieldValue = params.get(fieldName);
                if ((fieldValue != null) && (fieldValue.length() > 0)) {
                    // Build hash data
                    hashData.append(fieldName);
                    hashData.append('=');
                    hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));

                    // Build query
                    query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString()));
                    query.append('=');
                    query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));

                    if (itr.hasNext()) {
                        query.append('&');
                        hashData.append('&');
                    }
                }
            }

            String queryUrl = query.toString();
            String vnp_SecureHash = hmacSHA512(vnpayProperties.getHashSecret(), hashData.toString());
            queryUrl += "&vnp_SecureHash=" + vnp_SecureHash;

            return vnpayProperties.getPayUrl() + "?" + queryUrl;

        } catch (Exception e) {
            throw new RuntimeException("Lỗi tạo URL thanh toán VNPay", e);
        }
    }

    // Xác thực chữ ký IPN/Return
    public boolean validateSignature(Map<String, String> params) {
        String receivedHash = params.get("vnp_SecureHash");
        if (receivedHash == null) {
            return false;
        }

        Map<String, String> hashParams = new HashMap<>(params);
        hashParams.remove("vnp_SecureHash");
        hashParams.remove("vnp_SecureHashType");

        List<String> fieldNames = new ArrayList<>(hashParams.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        try {
            Iterator<String> itr = fieldNames.iterator();
            while (itr.hasNext()) {
                String fieldName = itr.next();
                String fieldValue = hashParams.get(fieldName);
                if ((fieldValue != null) && (fieldValue.length() > 0)) {
                    hashData.append(fieldName);
                    hashData.append('=');
                    hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                    if (itr.hasNext()) {
                        hashData.append('&');
                    }
                }
            }
        } catch (Exception e) {
            return false;
        }

        String calculatedHash = hmacSHA512(vnpayProperties.getHashSecret(), hashData.toString());
        return calculatedHash.equalsIgnoreCase(receivedHash);
    }

    // Hàm mã hóa chuẩn có bắt lỗi try-catch từ VNPayConfig cũ
    private String hmacSHA512(final String key, final String data) {
        try {
            if (key == null || data == null) throw new NullPointerException();
            final Mac hmac512 = Mac.getInstance("HmacSHA512");
            byte[] hmacKeyBytes = key.getBytes();
            final SecretKeySpec secretKey = new SecretKeySpec(hmacKeyBytes, "HmacSHA512");
            hmac512.init(secretKey);
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] result = hmac512.doFinal(dataBytes);
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff)); // Lệnh & 0xff chống tràn bit
            }
            return sb.toString();
        } catch (Exception ex) {
            return "";
        }
    }
}