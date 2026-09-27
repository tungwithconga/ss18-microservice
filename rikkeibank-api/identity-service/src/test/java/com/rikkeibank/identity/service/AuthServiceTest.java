package com.rikkeibank.identity.service;

import com.rikkeibank.identity.dto.*;
import com.rikkeibank.identity.entity.RefreshToken;
import com.rikkeibank.identity.entity.User;
import com.rikkeibank.identity.exception.BusinessException;
import com.rikkeibank.identity.repository.RefreshTokenRepository;
import com.rikkeibank.identity.repository.UserRepository;
import com.rikkeibank.identity.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private StringRedisTemplate redisTemplate;

    @InjectMocks private AuthService authService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(1L)
                .username("testuser")
                .password("$2a$12$hashedPassword")
                .role(User.Role.CUSTOMER)
                .isActive(true)
                .customerId(42L)
                .build();
    }

    @Test
    void login_shouldReturnTokens_whenCredentialsValid() {
        // Given
        LoginRequest request = new LoginRequest("testuser", "password123");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("password123", mockUser.getPassword())).thenReturn(true);
        when(jwtUtil.generateAccessToken(anyLong(), anyString(), anyString(), anyLong()))
                .thenReturn("access.token.jwt");
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        AuthResponse response = authService.login(request);

        // Then
        assertThat(response.getAccessToken()).isEqualTo("access.token.jwt");
        assertThat(response.getRefreshToken()).isNotNull();
        assertThat(response.getTokenType()).isEqualTo("Bearer");
    }

    @Test
    void login_shouldThrow401_whenPasswordWrong() {
        // Given
        LoginRequest request = new LoginRequest("testuser", "wrongpassword");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("wrongpassword", mockUser.getPassword())).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void login_shouldThrow403_whenAccountLocked() {
        // Given
        mockUser.setIsActive(false);
        LoginRequest request = new LoginRequest("testuser", "password123");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(mockUser));

        // When & Then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("locked");
    }

    @Test
    void register_shouldThrow409_whenUsernameExists() {
        // Given
        RegisterRequest request = new RegisterRequest("testuser", "pass", "CUSTOMER", null, null);
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void refresh_shouldThrow401_whenTokenRevoked() {
        // Given
        RefreshToken revokedToken = RefreshToken.builder()
                .token("revoked-token")
                .userId(1L)
                .revoked(true)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        when(refreshTokenRepository.findByToken("revoked-token")).thenReturn(Optional.of(revokedToken));

        // When & Then
        assertThatThrownBy(() -> authService.refresh("revoked-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expired or revoked");
    }
}
