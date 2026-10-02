package com.longdq.adaptengbackend.common.security;

import com.longdq.adaptengbackend.modules.user.entity.User;
import com.longdq.adaptengbackend.common.security.SecurityUtils;
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

        // [QUAN TRỌNG] 2. THẢ CỬA CHO CÁC API THI ĐẦU VÀO VÀ ĐĂNG NHẬP
        // Dù user chưa có level nào, vẫn phải cho họ gọi API lấy đề thi để làm bài chứ không được chặn!
        String requestURI = request.getRequestURI();
        if (requestURI.contains("/api/v1/auth") ||
                requestURI.contains("/admin/") ||     // Thả cửa cho endpoint mồi đề AI (DevGeneratorController, chỉ tồn tại ở profile dev)
                requestURI.contains("/placement-test") || // Thả cửa cho API thi Writing (start & submit)
                requestURI.contains("/writing-level") ||  // Thả cửa cho API lưu level Writing
                requestURI.contains("/test/generate") ||  // Thả cửa cho API thi Reading
                requestURI.contains("/test/submit") ||    // Thả cửa cho API nộp Reading
                requestURI.contains("/users/level")) {    // Thả cửa cho API lưu level Reading
            return true;
        }

        try {
            // 3. Lấy thông tin user
            User user = SecurityUtils.getCurrentUser();

            // 4. LOGIC MỚI: Chỉ chặn khi User CHƯA CÓ CẢ 2 LEVEL (Cả Reading và Writing đều null)
            if (user != null && user.getCurrentLevel() == null && user.getWritingCurrentLevel() == null) {

                // ĐÓNG GÓI JSON TRẢ VỀ CHO FRONTEND BÁO LỖI 403
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"message\": \"REQUIRE_PLACEMENT_TEST\"}");

                return false; // "Quay xe! Không cho đi tiếp vào Controller nữa"
            }
        } catch (Exception e) {
            // Nếu có lỗi khác (vd: chưa đăng nhập) thì kệ nó, cho đi qua để Security tự chặn
        }

        return true; // Cho phép đi tiếp vào Controller
    }
}