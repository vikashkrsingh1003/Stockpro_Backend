package com.apigateway.apiservice.filter;

import com.apigateway.apiservice.util.JwtUtil;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;

@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private static final Set<String> PUBLIC_ENDPOINTS = Set.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/forgot-password",
            "/oauth2",
            "/login/oauth2",
            "/swagger-ui",
            "/swagger-ui.html",
            "/v3/api-docs",
            "/auth/v3/api-docs",
            "/product/v3/api-docs",
            "/warehouse/v3/api-docs",
            "/supplier/v3/api-docs",
            "/movement/v3/api-docs",
            "/purchase/v3/api-docs",
            "/payment/v3/api-docs",
            "/analytics/v3/api-docs",
            "/alert/v3/api-docs"
    );

    private static final Map<String, Set<String>> ROLE_RULES = Map.ofEntries(
            Map.entry("/api/v1/products", Set.of("STAFF", "MANAGER", "OFFICER", "ADMIN")),
            Map.entry("/api/v1/warehouses", Set.of("STAFF", "MANAGER", "OFFICER", "ADMIN")),
            Map.entry("/api/v1/movements", Set.of("STAFF", "MANAGER", "ADMIN")),
            Map.entry("/api/v1/purchase-orders", Set.of("STAFF", "MANAGER", "OFFICER", "ADMIN")),
            Map.entry("/api/v1/suppliers", Set.of("STAFF", "MANAGER", "OFFICER", "ADMIN")),
            Map.entry("/api/v1/analytics/dashboard", Set.of("STAFF", "MANAGER", "OFFICER", "ADMIN")),
            Map.entry("/api/v1/analytics", Set.of("MANAGER", "ADMIN", "OFFICER")),
            Map.entry("/api/v1/alerts", Set.of("STAFF", "MANAGER", "OFFICER", "ADMIN")),
            Map.entry("/api/v1/payments", Set.of("MANAGER", "ADMIN")),
            Map.entry("/api/v1/auth/users", Set.of("ADMIN")),
            Map.entry("/api/v1/auth/deactivate", Set.of("ADMIN"))
    );

    private final JwtUtil jwtUtil;

    public AuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isPublic(path)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return reject(exchange.getResponse(), HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.substring(7);
        try {
            jwtUtil.validateToken(token);
            String role = jwtUtil.extractRole(token);
            if (role == null || role.isBlank()) {
                return reject(exchange.getResponse(), HttpStatus.FORBIDDEN);
            }
            Set<String> allowedRoles = allowedRoles(path);
            if (allowedRoles != null && !allowedRoles.contains(role)) {
                return reject(exchange.getResponse(), HttpStatus.FORBIDDEN);
            }
        } catch (Exception ex) {
            return reject(exchange.getResponse(), HttpStatus.UNAUTHORIZED);
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private boolean isPublic(String path) {
        return PUBLIC_ENDPOINTS.stream().anyMatch(path::startsWith);
    }

    private Set<String> allowedRoles(String path) {
        return ROLE_RULES.entrySet().stream()
                .filter(entry -> path.startsWith(entry.getKey()))
                .max((left, right) -> Integer.compare(left.getKey().length(), right.getKey().length()))
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    private Mono<Void> reject(ServerHttpResponse response, HttpStatus status) {
        response.setStatusCode(status);
        return response.setComplete();
    }
}
