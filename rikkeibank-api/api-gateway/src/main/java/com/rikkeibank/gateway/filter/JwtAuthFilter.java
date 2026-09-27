package com.rikkeibank.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT Authentication Filter — chạy trước mọi request tới các service.
 * Kiểm tra: (1) Có JWT không? (2) JWT còn hiệu lực? (3) Token có trong blacklist Redis không?
 * Nếu hợp lệ: forward request + inject X-User-Id, X-User-Role vào header.
 */
@Slf4j
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    // Public endpoints không cần token
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh"
    );

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public JwtAuthFilter(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Bỏ qua public endpoints
        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        // Check Redis blacklist trước
        String blacklistKey = "jwt:blacklist:" + token;
        return redisTemplate.hasKey(blacklistKey)
                .flatMap(isBlacklisted -> {
                    if (Boolean.TRUE.equals(isBlacklisted)) {
                        return unauthorized(exchange, "Token has been revoked");
                    }

                    try {
                        Claims claims = parseToken(token);
                        String userId = claims.getSubject();
                        String role = claims.get("role", String.class);
                        String username = claims.get("username", String.class);

                        // Inject thông tin user vào header để downstream services sử dụng
                        ServerWebExchange mutatedExchange = exchange.mutate()
                                .request(r -> r.header("X-User-Id", userId)
                                        .header("X-User-Role", role)
                                        .header("X-Username", username))
                                .build();

                        return chain.filter(mutatedExchange);
                    } catch (Exception e) {
                        log.warn("JWT validation failed: {}", e.getMessage());
                        return unauthorized(exchange, "Invalid or expired token");
                    }
                });
    }

    private Claims parseToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        log.warn("Unauthorized access: {} — Path: {}", message, exchange.getRequest().getURI().getPath());
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -100; // Ưu tiên cao nhất
    }
}
