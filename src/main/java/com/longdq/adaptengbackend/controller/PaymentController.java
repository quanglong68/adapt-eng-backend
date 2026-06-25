package com.longdq.adaptengbackend.controller;

import com.longdq.adaptengbackend.dto.CreatePaymentUrlRequestDto;
import com.longdq.adaptengbackend.dto.CreatePaymentUrlResponseDto;
import com.longdq.adaptengbackend.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create-url")
    public CreatePaymentUrlResponseDto createPaymentUrl(@Valid @RequestBody CreatePaymentUrlRequestDto request) {
        return paymentService.createPaymentUrl(request);
    }

    @GetMapping("/vnpay-ipn")
    public Map<String, String> handleVnpayIpn(@RequestParam Map<String, String> params) {
        return paymentService.handleVnpayIpn(params);
    }

    @GetMapping("/vnpay-return")
    public ResponseEntity<Map<String, Object>> handleVnpayReturn(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(paymentService.handleVnpayReturn(params));
    }
}
