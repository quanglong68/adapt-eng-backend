package com.longdq.adaptengbackend.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Tắt tính năng chống tấn công CSRF (Vì chúng ta đang dùng API Stateless, không cần thiết)
                .csrf(AbstractHttpConfigurer::disable)

                // Cấu hình phân quyền các đường link
                .authorizeHttpRequests(auth -> auth
                        // 1. Mở cửa hoàn toàn cho các API test AI
                        .requestMatchers("/api/test/**").permitAll()

                        // 2. Tạm thời mở cửa luôn cho các API của bạn để test cho tiện (Sau này làm chức năng Login/Token thì sẽ khóa lại sau)
                        .requestMatchers("/api/v1/**").permitAll()

                        // Các đường link khác thì bắt buộc phải đăng nhập
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
