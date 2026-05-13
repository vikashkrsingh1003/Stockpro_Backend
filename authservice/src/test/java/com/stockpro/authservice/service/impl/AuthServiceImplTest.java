package com.stockpro.authservice.service.impl;

import com.stockpro.authservice.dto.AdminCreateUserRequest;
import com.stockpro.authservice.dto.AuthResponse;
import com.stockpro.authservice.dto.LoginRequest;
import com.stockpro.authservice.dto.RegisterRequest;
import com.stockpro.authservice.entities.Role;
import com.stockpro.authservice.entities.User;
import com.stockpro.authservice.exception.ResourceNotFoundException;
import com.stockpro.authservice.exception.UserAlreadyExistsException;
import com.stockpro.authservice.repository.UserRepository;
import com.stockpro.authservice.service.EmailOtpService;
import com.stockpro.authservice.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private EmailOtpService emailOtpService;

    @InjectMocks
    private AuthServiceImpl service;

    private User user;

    @BeforeEach
    void setup() {
        user = User.builder()
                .userId("u1")
                .fullName("Test User")
                .email("test@mail.com")
                .passwordHash("encoded")
                .phone("9999999999")
                .department("Inventory")
                .role(Role.STAFF)
                .active(true)
                .build();
    }

    @Test
    void register_success_defaultsToStaffAndReturnsTokens() {
        RegisterRequest request = registerRequest();
        when(userRepository.existsByEmail("test@mail.com")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("encoded");
        when(jwtUtil.generateToken(any(User.class))).thenReturn("jwt");
        when(jwtUtil.generateRefreshToken(any(User.class))).thenReturn("refresh");

        AuthResponse response = service.register(request);

        verify(emailOtpService).verifyOtp("test@mail.com", "123456", EmailOtpService.REGISTRATION);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(Role.STAFF, captor.getValue().getRole());
        assertTrue(captor.getValue().isActive());
        assertEquals("jwt", response.getToken());
        assertEquals("refresh", response.getRefreshToken());
    }

    @Test
    void register_shouldThrow_whenEmailExists() {
        RegisterRequest request = registerRequest();
        when(userRepository.existsByEmail("test@mail.com")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> service.register(request));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(emailOtpService);
    }

    @Test
    void sendRegistrationOtp_success() {
        RegisterRequest request = registerRequest();
        when(userRepository.existsByEmail("test@mail.com")).thenReturn(false);

        service.sendRegistrationOtp(request);

        verify(emailOtpService).sendOtp("test@mail.com", EmailOtpService.REGISTRATION);
    }

    @Test
    void sendForgotPasswordOtp_success() {
        when(userRepository.findByEmail("test@mail.com")).thenReturn(Optional.of(user));

        service.sendForgotPasswordOtp("test@mail.com");

        verify(emailOtpService).sendOtp("test@mail.com", EmailOtpService.PASSWORD_RESET);
    }

    @Test
    void resetPasswordWithOtp_success() {
        when(userRepository.findByEmail("test@mail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new")).thenReturn("new-encoded");

        service.resetPasswordWithOtp("test@mail.com", "123456", "new");

        verify(emailOtpService).verifyOtp("test@mail.com", "123456", EmailOtpService.PASSWORD_RESET);
        assertEquals("new-encoded", user.getPasswordHash());
        verify(userRepository).save(user);
    }

    @Test
    void login_success() {
        LoginRequest request = loginRequest("secret");
        when(userRepository.findByEmail("test@mail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "encoded")).thenReturn(true);
        when(jwtUtil.generateToken(user)).thenReturn("jwt");
        when(jwtUtil.generateRefreshToken(user)).thenReturn("refresh");

        AuthResponse response = service.login(request);

        assertEquals("jwt", response.getToken());
        assertNotNull(user.getLastLoginAt());
        verify(userRepository).save(user);
    }

    @Test
    void login_notFound() {
        when(userRepository.findByEmail("test@mail.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.login(loginRequest("secret")));
    }

    @Test
    void login_invalidPassword() {
        when(userRepository.findByEmail("test@mail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad", "encoded")).thenReturn(false);

        assertThrows(RuntimeException.class, () -> service.login(loginRequest("bad")));
        verify(jwtUtil, never()).generateToken(any());
    }

    @Test
    void login_inactiveUser() {
        user.setActive(false);
        when(userRepository.findByEmail("test@mail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "encoded")).thenReturn(true);

        assertThrows(RuntimeException.class, () -> service.login(loginRequest("secret")));
    }

    @Test
    void logout_isStatelessNoOp() {
        service.logout("token");

        verifyNoInteractions(jwtUtil);
    }

    @Test
    void validateToken_delegatesToJwtUtil() {
        when(jwtUtil.validateToken("token")).thenReturn(true);

        assertTrue(service.validateToken("token"));
    }

    @Test
    void refreshToken_success() {
        when(jwtUtil.extractUsername("refresh")).thenReturn("test@mail.com");
        when(userRepository.findByEmail("test@mail.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(user)).thenReturn("new-jwt");
        when(jwtUtil.generateRefreshToken(user)).thenReturn("new-refresh");

        AuthResponse response = service.refreshToken("refresh");

        assertEquals("new-jwt", response.getToken());
        assertEquals("new-refresh", response.getRefreshToken());
    }

    @Test
    void getUserById_success() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        assertEquals("test@mail.com", service.getUserById("u1").getEmail());
    }

    @Test
    void getUserById_notFound() {
        when(userRepository.findById("u1")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getUserById("u1"));
    }

    @Test
    void getUserByEmail_success() {
        when(userRepository.findByEmail("test@mail.com")).thenReturn(Optional.of(user));

        assertEquals("u1", service.getUserByEmail("test@mail.com").getUserId());
    }

    @Test
    void getUserByEmail_notFound() {
        when(userRepository.findByEmail("missing@mail.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getUserByEmail("missing@mail.com"));
    }

    @Test
    void updateProfileByEmail_success() {
        RegisterRequest request = registerRequest();
        request.setFullName("Updated User");
        when(userRepository.findByEmail("test@mail.com")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        User saved = service.updateProfileByEmail("test@mail.com", request);

        assertEquals("Updated User", saved.getFullName());
        verify(userRepository).save(user);
    }

    @Test
    void changePasswordByEmail_success() {
        when(userRepository.findByEmail("test@mail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "encoded")).thenReturn(true);
        when(passwordEncoder.encode("new")).thenReturn("new-encoded");

        service.changePasswordByEmail("test@mail.com", "old", "new");

        assertEquals("new-encoded", user.getPasswordHash());
        verify(userRepository).save(user);
    }

    @Test
    void changePasswordByEmail_wrongOldPassword() {
        when(userRepository.findByEmail("test@mail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad", "encoded")).thenReturn(false);

        assertThrows(RuntimeException.class, () -> service.changePasswordByEmail("test@mail.com", "bad", "new"));
    }

    @Test
    void deactivateUser_togglesActive() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        service.deactivateUser("u1");

        assertFalse(user.isActive());
        verify(userRepository).save(user);
    }

    @Test
    void deleteUser_success() {
        when(userRepository.existsById("u1")).thenReturn(true);

        service.deleteUser("u1");

        verify(userRepository).deleteById("u1");
    }

    @Test
    void deleteUser_notFound() {
        when(userRepository.existsById("u1")).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.deleteUser("u1"));
    }

    @Test
    void updateUserRole_success() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        service.updateUserRole("u1", "manager");

        assertEquals(Role.MANAGER, user.getRole());
        verify(userRepository).save(user);
    }

    @Test
    void updateUserRole_invalidRole() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        assertThrows(RuntimeException.class, () -> service.updateUserRole("u1", "OWNER"));
    }

    @Test
    void getAllUsersScoped_adminGetsAll() {
        user.setRole(Role.ADMIN);
        when(userRepository.findByEmail("test@mail.com")).thenReturn(Optional.of(user));
        when(userRepository.findAll()).thenReturn(List.of(user));

        assertEquals(1, service.getAllUsersScoped("test@mail.com").size());
    }

    @Test
    void getAllUsersScoped_managerGetsDepartmentUsers() {
        user.setRole(Role.MANAGER);
        when(userRepository.findByEmail("test@mail.com")).thenReturn(Optional.of(user));
        when(userRepository.findByDepartment("Inventory")).thenReturn(List.of(user));

        assertEquals(1, service.getAllUsersScoped("test@mail.com").size());
    }

    @Test
    void getAllUsersScoped_staffGetsEmptyList() {
        when(userRepository.findByEmail("test@mail.com")).thenReturn(Optional.of(user));

        assertTrue(service.getAllUsersScoped("test@mail.com").isEmpty());
    }

    @Test
    void createUserByAdmin_success() {
        AdminCreateUserRequest request = adminRequest("ADMIN");
        when(userRepository.existsByEmail("admin@mail.com")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("encoded");
        when(jwtUtil.generateToken(any(User.class))).thenReturn("jwt");
        when(jwtUtil.generateRefreshToken(any(User.class))).thenReturn("refresh");

        AuthResponse response = service.createUserByAdmin(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(Role.ADMIN, captor.getValue().getRole());
        assertEquals("jwt", response.getToken());
    }

    @Test
    void createUserByAdmin_duplicateEmail() {
        AdminCreateUserRequest request = adminRequest("ADMIN");
        when(userRepository.existsByEmail("admin@mail.com")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> service.createUserByAdmin(request));
    }

    @Test
    void createUserByAdmin_invalidRole() {
        AdminCreateUserRequest request = adminRequest("OWNER");
        when(userRepository.existsByEmail("admin@mail.com")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("encoded");

        assertThrows(IllegalArgumentException.class, () -> service.createUserByAdmin(request));
    }

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Test User");
        request.setEmail("test@mail.com");
        request.setPassword("secret");
        request.setPhone("9999999999");
        request.setDepartment("Inventory");
        request.setOtp("123456");
        return request;
    }

    private LoginRequest loginRequest(String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@mail.com");
        request.setPassword(password);
        return request;
    }

    private AdminCreateUserRequest adminRequest(String role) {
        AdminCreateUserRequest request = new AdminCreateUserRequest();
        request.setFullName("Admin User");
        request.setEmail("admin@mail.com");
        request.setPassword("secret");
        request.setPhone("8888888888");
        request.setDepartment("Admin");
        request.setRole(role);
        return request;
    }
}
