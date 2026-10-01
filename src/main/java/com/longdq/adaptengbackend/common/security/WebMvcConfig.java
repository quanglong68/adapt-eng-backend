package com.longdq.adaptengbackend.common.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.longdq.adaptengbackend.modules.auth.controller.AuthController;
import com.longdq.adaptengbackend.modules.payment.controller.PaymentController;
import com.longdq.adaptengbackend.modules.toeic.controller.ToeicController;
import com.longdq.adaptengbackend.modules.user.controller.UserController;
import com.longdq.adaptengbackend.modules.user.controller.UsersController;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final PlacementTestInterceptor placementTestInterceptor;

    public WebMvcConfig(PlacementTestInterceptor placementTestInterceptor) {
        this.placementTestInterceptor = placementTestInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(placementTestInterceptor)
                // 1. CHẶN TẤT CẢ CÁC API TRONG HỆ THỐNG
                .addPathPatterns("/api/v1/**")

                // 2. NGOẠI TRỪ CÁC API SAU CHO KHỚP 100% VỚI CONTROLLER
                .excludePathPatterns(
                        "/api/v1/auth/**",            // Khớp AuthController: Đăng nhập, Đăng ký
                        "/api/v1/users/me",           // Khớp UsersController: Lấy thông tin Profile
                        "/api/v1/user/set-track",     // Khớp UserController: Cho phép chọn Lộ trình trước khi làm Test
                        "/api/v1/test/**",
                        "/api/v1/toeic/test/**",
                        "/api/v1/toeic/test/**",      // Khớp ToeicController: Sinh đề và Nộp bài Test đầu vào
                        "/api/v1/payment/**"          // Khớp PaymentController: VNPAY webhook

                );
    }
}