package com.rikkeibank.identity.service;

import com.rikkeibank.identity.dto.*;
import com.rikkeibank.identity.entity.RefreshToken;
import com.rikkeibank.identity.entity.User;
import com.rikkeibank.identity.exception.BusinessException;
import com.rikkeibank.identity.repository.RefreshTokenRepository;
import com.rikkeibank.identity.repository.UserRepository;
import com.rikkeibank.identity.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;  // ms

    /**
     * Đăng ký tài khoản mới
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Username already exists: " + request.getUsername());
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(User.Role.valueOf(request.getRole()))
                .customerId(request.getCustomerId())
                .staffId(request.getStaffId())
                .build();

        User saved = userRepository.save(user);
        log.info("New user registered: {} with role {}", saved.getUsername(), saved.getRole());
        return UserResponse.from(saved);
    }

    /**
     * Đăng nhập — trả về Access Token + Refresh Token
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!user.getIsActive()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Account is locked");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String accessToken = jwtUtil.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole().name(), user.getCustomerId()
        );

        // Tạo Refresh Token opaque
        String refreshTokenValue = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .userId(user.getId())
                .token(refreshTokenValue)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiry / 1000))
                .build();
        refreshTokenRepository.save(refreshToken);

        log.info("User logged in: {}", user.getUsername());
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .tokenType("Bearer")
                .build();
    }

    /**
     * Refresh Access Token dùng Refresh Token
     */
    @Transactional
    public AuthResponse refresh(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (!refreshToken.isValid()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Refresh token expired or revoked");
        }

        User user = userRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found"));

        // Rotation: invalidate old, create new refresh token
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        String newRefreshValue = UUID.randomUUID().toString();
        RefreshToken newRefreshToken = RefreshToken.builder()
                .userId(user.getId())
                .token(newRefreshValue)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiry / 1000))
                .build();
        refreshTokenRepository.save(newRefreshToken);

        String newAccessToken = jwtUtil.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole().name(), user.getCustomerId()
        );

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshValue)
                .tokenType("Bearer")
                .build();
    }

    /**
     * Logout — vô hiệu hóa refresh token
     */
    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokenRepository.findByToken(refreshTokenValue).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });
    }

    /**
     * ADMIN force logout — thu hồi mọi token của user, blacklist access token hiện tại trên Redis
     */
    @Transactional
    public void revokeUserAccess(Long targetUserId, String currentAccessToken) {
        // Thu hồi tất cả refresh tokens
        List<RefreshToken> tokens = refreshTokenRepository.findByUserId(targetUserId);
        tokens.forEach(t -> t.setRevoked(true));
        refreshTokenRepository.saveAll(tokens);

        // Blacklist access token hiện tại trên Redis (TTL = thời gian còn lại của token)
        if (currentAccessToken != null && jwtUtil.isTokenValid(currentAccessToken)) {
            long remainingMs = jwtUtil.getRemainingExpiry(currentAccessToken);
            if (remainingMs > 0) {
                String blacklistKey = "jwt:blacklist:" + currentAccessToken;
                redisTemplate.opsForValue().set(blacklistKey, "revoked", remainingMs, TimeUnit.MILLISECONDS);
            }
        }

        log.warn("ADMIN revoked access for userId: {}", targetUserId);
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse updateUserStatus(Long userId, boolean isActive) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found"));
        user.setIsActive(isActive);
        return UserResponse.from(userRepository.save(user));
    }
}
