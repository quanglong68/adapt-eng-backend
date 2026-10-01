package com.longdq.adaptengbackend.modules.auth.service;

import com.longdq.adaptengbackend.modules.auth.dto.AuthResponseDto;
import com.longdq.adaptengbackend.modules.auth.dto.LoginRequestDto;
import com.longdq.adaptengbackend.modules.auth.dto.RegisterRequestDto;
import com.longdq.adaptengbackend.modules.user.entity.User;
import com.longdq.adaptengbackend.common.exception.DuplicateResourceException;
import com.longdq.adaptengbackend.common.exception.ResourceNotFoundException;
import com.longdq.adaptengbackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import com.longdq.adaptengbackend.common.security.JwtService;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthResponseDto register(RegisterRequestDto request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new DuplicateResourceException("Email này đã được sử dụng!");
        }

        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setCreatedAt(LocalDateTime.now());

        userRepository.save(user);

        String jwtToken = jwtService.generateToken(user);

        return new AuthResponseDto(
                jwtToken,
                user.getId().toString(),
                user.getEmail(),
                user.getFullName(),
                null
        );
    }

    public AuthResponseDto login(LoginRequestDto request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng!"));

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
