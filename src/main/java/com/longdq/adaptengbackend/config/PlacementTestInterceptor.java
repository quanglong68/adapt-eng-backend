package com.longdq.adaptengbackend.config;

import com.longdq.adaptengbackend.entity.User;
import com.longdq.adaptengbackend.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class PlacementTestInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. Bỏ qua các request OPTIONS (Của cơ chế CORS trên trình duyệt)
        if (request.getMethod().equalsIgnoreCase("OPTIONS")) {
            return true;
        }

        try {
            // 2. Lấy thông tin user
            User user = SecurityUtils.getCurrentUser();

            // 3. Nếu User đã đăng nhập nhưng cột Level đang bị rỗng
            if (user != null && user.getCurrentLevel() == null) {

                // ĐÓNG GÓI JSON TRẢ VỀ CHO FRONTEND (Không dùng throw Exception nữa)
                response.setStatus(HttpServletResponse.SC_FORBIDDEN); // Trả về mã 403 Forbidden
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"message\": \"REQUIRE_PLACEMENT_TEST\"}");

                return false; // Lệnh này có ý nghĩa: "Quay xe! Không cho đi tiếp vào Controller nữa"
            }
        } catch (Exception e) {
            // Nếu có lỗi khác (vd: chưa đăng nhập) thì kệ nó, cho đi qua để Security tự chặn
        }

        return true; // Cho phép đi tiếp vào Controller
    }
}