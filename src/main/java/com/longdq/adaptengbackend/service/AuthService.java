package com.longdq.adaptengbackend.service;

import com.longdq.adaptengbackend.dto.AuthResponseDto;
import com.longdq.adaptengbackend.dto.LoginRequestDto;
import com.longdq.adaptengbackend.dto.RegisterRequestDto;
import com.longdq.adaptengbackend.entity.User;
import com.longdq.adaptengbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    // 1. Logic Đăng ký tài khoản
    public AuthResponseDto register(RegisterRequestDto request) {
        // Kiểm tra xem email đã tồn tại chưa
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email này đã được sử dụng!");
        }

        // Tạo user mới và băm mật khẩu
        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword())); // BĂM MẬT KHẨU ở đây
        user.setCreatedAt(LocalDateTime.now());

        userRepository.save(user);

        // Tạo vòng tay JWT cho user mới luôn
        String jwtToken = jwtService.generateToken(user);

        return new AuthResponseDto(
                jwtToken,
                user.getId().toString(),
                user.getEmail(),
                user.getFullName(),
                null
        );
    }

    // 2. Logic Đăng nhập
    public AuthResponseDto login(LoginRequestDto request) {
        // Hàm này sẽ tự động kiểm tra email và mật khẩu (đã băm) có khớp nhau không
        // Nếu sai mật khẩu hoặc sai email, nó sẽ tự văng ngoại lệ BadCredentialsException
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // Nếu đi đến được dòng này tức là đăng nhập thành công, lấy user ra để tạo token
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

        String jwtToken = jwtService.generateToken(user);

        return new AuthResponseDto(
                jwtToken,
                user.getId().toString(),
                user.getEmail(),
                user.getFullName(),
                user.getLearningTrack()
        );
    }
}