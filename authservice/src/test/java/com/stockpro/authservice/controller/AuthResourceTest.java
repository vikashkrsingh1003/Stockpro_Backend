package com.stockpro.authservice.controller;

import com.stockpro.authservice.dto.AdminCreateUserRequest;
import com.stockpro.authservice.dto.AuthResponse;
import com.stockpro.authservice.dto.EmailRequest;
import com.stockpro.authservice.dto.ForgotPasswordResetRequest;
import com.stockpro.authservice.dto.LoginRequest;
import com.stockpro.authservice.dto.RegisterRequest;
import com.stockpro.authservice.entities.Role;
import com.stockpro.authservice.entities.User;
import com.stockpro.authservice.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthResourceTest {

    @Mock
    private AuthService authService;

    @Mock
    private Authentication authentication;

    private AuthResource controller;

    @BeforeEach
    void setup() {
        controller = new AuthResource(authService);
    }

    @Test
    void register_returnsAuthResponse() {
        RegisterRequest request = new RegisterRequest();
        AuthResponse response = new AuthResponse("token", "refresh");
        when(authService.register(request)).thenReturn(response);

        assertSame(response, controller.register(request).getBody());
    }

//    @Test
//    void otpEndpointsDelegateToService() {
//        RegisterRequest registerRequest = new RegisterRequest();
//        registerRequest.setEmail("new@test.com");
//        EmailRequest emailRequest = new EmailRequest();
//        emailRequest.setEmail("user@test.com");
//        ForgotPasswordResetRequest resetRequest = new ForgotPasswordResetRequest();
//        resetRequest.setEmail("user@test.com");
//        resetRequest.setOtp("123456");
//        resetRequest.setNewPassword("new");
//
//        assertEquals("OTP sent to email", controller.sendRegistrationOtp(registerRequest).getBody());
//        assertEquals("OTP sent to email", controller.sendForgotPasswordOtp(emailRequest).getBody());
//        assertEquals("Password reset successfully", controller.resetForgotPassword(resetRequest).getBody());
//
//        verify(authService).sendRegistrationOtp(registerRequest);
//        verify(authService).sendForgotPasswordOtp("user@test.com");
//        verify(authService).resetPasswordWithOtp("user@test.com", "123456", "new");
//    }

    @Test
    void login_returnsAuthResponse() {
        LoginRequest request = new LoginRequest();
        AuthResponse response = new AuthResponse("token", "refresh");
        when(authService.login(request)).thenReturn(response);

        assertSame(response, controller.login(request).getBody());
    }

    @Test
    void logout_delegatesAndReturnsMessage() {
        assertEquals("Logged out successfully", controller.logout("token").getBody());
        verify(authService).logout("token");
    }

    @Test
    void refresh_returnsNewToken() {
        AuthResponse response = new AuthResponse("new", "refresh");
        when(authService.refreshToken("refresh")).thenReturn(response);

        assertSame(response, controller.refresh("refresh").getBody());
    }

    @Test
    void profileMethodsUseAuthenticatedEmail() {
        when(authentication.getName()).thenReturn("user@test.com");
        User user = user("u1", "user@test.com", Role.STAFF);
        when(authService.getUserByEmail("user@test.com")).thenReturn(user);
        when(authService.updateProfileByEmail(eq("user@test.com"), any(RegisterRequest.class))).thenReturn(user);

        assertEquals("user@test.com", controller.getProfile(authentication).getBody().getEmail());
        assertEquals("user@test.com", controller.updateProfile(authentication, new RegisterRequest()).getBody().getEmail());
    }

    @Test
    void adminUserOperationsDelegateToService() {
        AdminCreateUserRequest request = new AdminCreateUserRequest();
        AuthResponse response = new AuthResponse("admin-token", "refresh");
        when(authService.createUserByAdmin(request)).thenReturn(response);

        assertSame(response, controller.createUser(request).getBody());
        assertEquals("User deactivated", controller.deactivateUser("u1").getBody());
        assertEquals("User permanently deleted", controller.deleteUser("u1").getBody());
        assertEquals("User role updated successfully", controller.updateUserRole("u1", "MANAGER").getBody());
        verify(authService).deactivateUser("u1");
        verify(authService).deleteUser("u1");
        verify(authService).updateUserRole("u1", "MANAGER");
    }

    @Test
    void getAllUsersMapsUsersToDtos() {
        when(authentication.getName()).thenReturn("admin@test.com");
        when(authService.getAllUsersScoped("admin@test.com")).thenReturn(List.of(user("u1", "staff@test.com", Role.STAFF)));

        var users = controller.getAllUsers(authentication).getBody();

        assertEquals(1, users.size());
        assertEquals("staff@test.com", users.get(0).getEmail());
    }

    @Test
    void internalAlertEmailsRequiresInternalHeaderAndReturnsEmails() {
        when(authService.getActiveUserEmails()).thenReturn(List.of("admin@test.com", "staff@test.com"));

        var response = controller.getAlertEmailRecipients("true");

        assertEquals(List.of("admin@test.com", "staff@test.com"), response.getBody());
        assertThrows(ResponseStatusException.class, () -> controller.getAlertEmailRecipients(null));
    }

    @Test
    void changePasswordDelegatesUsingAuthenticatedEmail() {
        when(authentication.getName()).thenReturn("user@test.com");

        assertEquals("Password updated", controller.changePassword(authentication, "old", "new").getBody());

        verify(authService).changePasswordByEmail("user@test.com", "old", "new");
    }

    private User user(String id, String email, Role role) {
        return User.builder()
                .userId(id)
                .fullName("Test User")
                .email(email)
                .phone("999")
                .department("Ops")
                .role(role)
                .active(true)
                .build();
    }
}
