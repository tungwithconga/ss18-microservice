package com.rikkeibank.identity.controller;

import com.rikkeibank.identity.dto.*;
import com.rikkeibank.identity.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AuthController — xử lý đăng ký, đăng nhập, refresh token, force logout
 * Tất cả request đi qua API Gateway trước khi tới đây.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * POST /api/auth/register — Đăng ký tài khoản (PUBLIC)
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /**
     * POST /api/auth/login — Đăng nhập (PUBLIC)
     * Returns: access_token (JWT 15 phút) + refresh_token (7 ngày)
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * POST /api/auth/refresh — Lấy Access Token mới (PUBLIC)
     * Body: { "refreshToken": "..." }
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.refresh(body.get("refreshToken")));
    }

    /**
     * POST /api/auth/logout — Đăng xuất (AUTHENTICATED)
     * Body: { "refreshToken": "..." }
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody Map<String, String> body) {
        authService.logout(body.get("refreshToken"));
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/auth/revoke/{userId} — ADMIN force logout user bất kỳ
     * Header: X-Access-Token (token hiện tại của user đó, để blacklist trên Redis)
     */
    @PostMapping("/revoke/{userId}")
    public ResponseEntity<Void> revokeUserAccess(
            @PathVariable Long userId,
            @RequestHeader(value = "X-Target-Access-Token", required = false) String targetToken) {
        // Chỉ ADMIN mới được gọi — kiểm tra trong SecurityConfig
        authService.revokeUserAccess(userId, targetToken);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /api/auth/users — Danh sách tất cả users (ADMIN only)
     */
    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(authService.getAllUsers());
    }

    /**
     * PUT /api/auth/users/{id}/status — Kích hoạt/khóa user (ADMIN only)
     * Body: { "isActive": true/false }
     */
    @PutMapping("/users/{id}/status")
    public ResponseEntity<UserResponse> updateUserStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        return ResponseEntity.ok(authService.updateUserStatus(id, body.get("isActive")));
    }
}
