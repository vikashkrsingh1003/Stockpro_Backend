package com.stockpro.authservice.security;

import com.stockpro.authservice.entities.Role;
import com.stockpro.authservice.entities.User;
import com.stockpro.authservice.repository.UserRepository;
import com.stockpro.authservice.util.JwtUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${stockpro.frontend.oauth2-success-url:http://localhost:4200/oauth2/callback}")
    private String frontendSuccessUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();

        String email = oauthUser.getAttribute("email");
        if (email == null || email.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Google account email is required");
            return;
        }

        String name = oauthUser.getAttribute("name");
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> createGoogleUser(email, name));

        if (!user.isActive()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "This account has been deactivated");
            return;
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String redirectUrl = UriComponentsBuilder.fromUriString(frontendSuccessUrl)
                .queryParam("token", jwtUtil.generateToken(user))
                .queryParam("refreshToken", jwtUtil.generateRefreshToken(user))
                .build()
                .toUriString();

        response.sendRedirect(redirectUrl);
    }

    private User createGoogleUser(String email, String name) {
        User user = new User();
        user.setUserId(UUID.randomUUID().toString());
        user.setEmail(email);
        user.setFullName(name != null && !name.isBlank() ? name : email);
        user.setPhone("");
        user.setDepartment("");
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setActive(true);
        user.setRole(Role.STAFF);
        return userRepository.save(user);
    }
}
