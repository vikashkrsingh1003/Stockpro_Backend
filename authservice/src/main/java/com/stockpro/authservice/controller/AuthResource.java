package com.stockpro.authservice.controller;

import com.stockpro.authservice.dto.*;
import com.stockpro.authservice.entities.User;
import com.stockpro.authservice.mapper.UserMapper;
import com.stockpro.authservice.service.AuthService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthResource {

    private final AuthService authService;

    
    // 1. Register — Public self-registration (defaults to STAFF)
    @PostMapping("/register/send-otp")
    public ResponseEntity<String> sendRegistrationOtp(@RequestBody RegisterRequest request) {
        authService.sendRegistrationOtp(request);
        return ResponseEntity.ok("OTP sent to email");
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/forgot-password/send-otp")
    public ResponseEntity<String> sendForgotPasswordOtp(@RequestBody EmailRequest request) {
        authService.sendForgotPasswordOtp(request.getEmail());
        return ResponseEntity.ok("OTP sent to email");
    }

    @PostMapping("/forgot-password/reset")
    public ResponseEntity<String> resetForgotPassword(@RequestBody ForgotPasswordResetRequest request) {
        authService.resetPasswordWithOtp(request.getEmail(), request.getOtp(), request.getNewPassword());
        return ResponseEntity.ok("Password reset successfully");
    }

    // 2. Login
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // 3. Logout
    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestParam String token) {
        authService.logout(token);
        return ResponseEntity.ok("Logged out successfully");
    }

    // 4. Refresh
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestParam String refreshToken) {
        return ResponseEntity.ok(authService.refreshToken(refreshToken));
    }

    // profile
    @GetMapping("/profile")
    public ResponseEntity<UserDto> getProfile(Authentication authentication) {

        String email = authentication.getName();

        return ResponseEntity.ok(
                UserMapper.toDto(authService.getUserByEmail(email))
        );
    }

    // 6. Update Profile
    @PutMapping("/profile")
    public ResponseEntity<UserDto> updateProfile(Authentication authentication, @RequestBody RegisterRequest request) {
        
        String email = authentication.getName();
    
        // We fetch the user by email first, then update
        User updatedUser = authService.updateProfileByEmail(email, request);
        
        return ResponseEntity.ok(UserMapper.toDto(updatedUser));
    }


    // 7. Change Password
    @PutMapping("/password")
    public ResponseEntity<String> changePassword(Authentication authentication,
                                                 @RequestParam String oldPassword,
                                                 @RequestParam String newPassword) {
        String email = authentication.getName();
        authService.changePasswordByEmail(email, oldPassword, newPassword);
        return ResponseEntity.ok("Password updated");
    }

    // 8. Deactivate
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/deactivate")
    public ResponseEntity<String> deactivateUser(@RequestParam("userId") String userId) {
        authService.deactivateUser(userId);
        return ResponseEntity.ok("User deactivated");
    }

    // 9. Delete User (Hard Delete)
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<String> deleteUser(@PathVariable("userId") String userId) {
        authService.deleteUser(userId);
        return ResponseEntity.ok("User permanently deleted");
    }

    // 9.1 Create User (Admin only)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/users")
    public ResponseEntity<AuthResponse> createUser(@RequestBody AdminCreateUserRequest request) {
        return ResponseEntity.ok(authService.createUserByAdmin(request));
    }

    // 9.5 Update User Role (Admin only)
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/users/{userId}/role")
    public ResponseEntity<String> updateUserRole(@PathVariable("userId") String userId, @RequestParam("role") String role) {
        authService.updateUserRole(userId, role);
        return ResponseEntity.ok("User role updated successfully");
    }

    //10. Get All Users
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users")
    public ResponseEntity<List<UserDto>> getAllUsers(Authentication authentication) {
        
        String currentUserEmail = authentication.getName();

        List<UserDto> users = authService.getAllUsersScoped(currentUserEmail)
                .stream()
                .map(UserMapper::toDto)
                .toList();

        return ResponseEntity.ok(users);
    }

    @GetMapping("/internal/alert-emails")
    public ResponseEntity<List<String>> getAlertEmailRecipients(
            @RequestHeader(value = "X-Internal-Request", required = false) String internalRequest) {
        if (!"true".equalsIgnoreCase(internalRequest)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Internal service access only");
        }
        return ResponseEntity.ok(authService.getActiveUserEmails());
    }


}
