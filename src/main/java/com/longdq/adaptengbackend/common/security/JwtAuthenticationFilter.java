package com.longdq.adaptengbackend.common.security;

import com.longdq.adaptengbackend.common.security.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Lấy chuỗi token từ header "Authorization"
        String authHeader = request.getHeader("Authorization");
        String jwt = null;
        final String userEmail;

        // 2. KIỂM TRA HEADER HOẶC THAM SỐ URL (DÀNH CHO SSE)
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7); // Lấy từ Header
        } else {
            String tokenParam = request.getParameter("token");
            if (tokenParam != null && !tokenParam.isEmpty()) {
                jwt = tokenParam; // Lấy từ tham số URL (?token=...)
            }
        }

        // 3. Nếu vẫn không có token ở cả 2 nơi -> Cho đi tiếp (chặn hay không do SecurityConfig quyết định)
        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            userEmail = jwtService.extractUsername(jwt); // Giải mã lấy email

            // 4. Nếu có email và user chưa được xác thực trong Context hiện tại
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // Lấy thông tin user từ DB
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

                // Kiểm tra token có hợp lệ không
                if (jwtService.isTokenValid(jwt, userDetails)) {
                    // Nếu hợp lệ, tạo thẻ chứng nhận an toàn (Authentication Token)
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Cấp quyền đi vào hệ thống
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // NẾU TOKEN RÁC, HẾT HẠN, SAI CHỮ KÝ -> Bắt lỗi tại đây!
            log.warn("Token rejected: {}", e.getMessage());
        }

        // Chuyển cho màng lọc tiếp theo
        filterChain.doFilter(request, response);
    }
}