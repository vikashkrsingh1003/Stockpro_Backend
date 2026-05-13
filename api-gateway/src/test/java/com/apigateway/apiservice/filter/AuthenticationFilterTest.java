package com.apigateway.apiservice.filter;

import com.apigateway.apiservice.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private GatewayFilterChain chain;

    private AuthenticationFilter filter;

    @BeforeEach
    void setup() {
        filter = new AuthenticationFilter(jwtUtil);
        lenient().when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void publicEndpointSkipsTokenValidation() {
        MockServerWebExchange exchange = exchange("/api/v1/auth/login", null);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void missingTokenReturnsUnauthorized() {
        MockServerWebExchange exchange = exchange("/api/v1/products", null);

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(exchange);
    }

    @Test
    void allowedRolePassesThrough() {
        MockServerWebExchange exchange = exchange("/api/v1/products", "Bearer token");
        when(jwtUtil.extractRole("token")).thenReturn("STAFF");

        filter.filter(exchange, chain).block();

        verify(jwtUtil).validateToken("token");
        verify(chain).filter(exchange);
    }

    @Test
    void forbiddenWhenRoleMissingOrNotAllowed() {
        MockServerWebExchange blankRole = exchange("/api/v1/products", "Bearer token");
        when(jwtUtil.extractRole("token")).thenReturn("");

        filter.filter(blankRole, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, blankRole.getResponse().getStatusCode());

        MockServerWebExchange disallowed = exchange("/api/v1/payments", "Bearer token2");
        when(jwtUtil.extractRole("token2")).thenReturn("STAFF");

        filter.filter(disallowed, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, disallowed.getResponse().getStatusCode());
    }

    @Test
    void invalidTokenReturnsUnauthorized() {
        MockServerWebExchange exchange = exchange("/api/v1/products", "Bearer token");
        doThrow(new RuntimeException("bad")).when(jwtUtil).validateToken("token");

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void getOrderRunsBeforeRouteFilters() {
        assertEquals(-1, filter.getOrder());
    }

    private MockServerWebExchange exchange(String path, String authorization) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get(path);
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return MockServerWebExchange.from(request);
    }
}
