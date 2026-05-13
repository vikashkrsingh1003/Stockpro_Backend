package com.stockpro.authservice.service.impl;

import com.stockpro.authservice.dto.AdminCreateUserRequest;
import com.stockpro.authservice.dto.AuthResponse;
import com.stockpro.authservice.dto.LoginRequest;
import com.stockpro.authservice.dto.RegisterRequest;
import com.stockpro.authservice.entities.User;
import com.stockpro.authservice.entities.Role;
import com.stockpro.authservice.exception.ResourceNotFoundException;
import com.stockpro.authservice.exception.UserAlreadyExistsException;

import com.stockpro.authservice.repository.UserRepository;
import com.stockpro.authservice.service.AuthService;
import com.stockpro.authservice.service.EmailOtpService;
import com.stockpro.authservice.util.JwtUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailOtpService emailOtpService;

    //  REGISTER
    @Override
    public AuthResponse register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Email already exists");
        }

        emailOtpService.verifyOtp(request.getEmail(), request.getOtp(), EmailOtpService.REGISTRATION);

        User user = new User();
        user.setUserId(UUID.randomUUID().toString());
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());       
        user.setDepartment(request.getDepartment()); 
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setActive(true);
        user.setRole(Role.STAFF);  //Self-registered users default to STAFF.

        userRepository.save(user);

        String token = jwtUtil.generateToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);

        return new AuthResponse(token, refreshToken);
    }

    @Override
    public void sendRegistrationOtp(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Email already exists");
        }
        emailOtpService.sendOtp(request.getEmail(), EmailOtpService.REGISTRATION);
    }

    @Override
    public void sendForgotPasswordOtp(String email) {
        getUserByEmail(email);
        emailOtpService.sendOtp(email, EmailOtpService.PASSWORD_RESET);
    }

    @Override
    public void resetPasswordWithOtp(String email, String otp, String newPassword) {
        User user = getUserByEmail(email);
        emailOtpService.verifyOtp(email, otp, EmailOtpService.PASSWORD_RESET);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    //  LOGIN
    @Override
    public AuthResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("No account found with this email."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid password. Please try again.");
        }

        //  Prevent inactive users from logging in
        if (!user.isActive()) {
            throw new RuntimeException("This account has been deactivated. Please contact support.");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String token = jwtUtil.generateToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);

        return new AuthResponse(token, refreshToken);
    }
    //  LOGOUT (Simple version)
    @Override
    public void logout(String token) {
        // Option 1: Do nothing (stateless JWT)
    	
        // Option 2: Add token to blacklist (Redis)
    }

    //  VALIDATE TOKEN
    @Override
    public boolean validateToken(String token) {
        return jwtUtil.validateToken(token);
    }

    //  REFRESH TOKEN
    @Override
    public AuthResponse refreshToken(String token) {
        String email = jwtUtil.extractUsername(token);
        User user = userRepository.findByEmail(email).orElseThrow();
        
        // Generate new access token and a new refresh token (standard practice)
        String newToken = jwtUtil.generateToken(user); 
        String newRefreshToken = jwtUtil.generateRefreshToken(user);
        
        return new AuthResponse(newToken, newRefreshToken);
    }



    @Override
    public User getUserById(String userId) {
        return userRepository.findById(userId)
                .or(() -> userRepository.findByUserId(userId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
    }

    // GET USER BY EMAIL
    @Override
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    //  UPDATE PROFILE BY EMAIL
    @Override
    public User updateProfileByEmail(String email, RegisterRequest request) {
        User user = getUserByEmail(email);
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setDepartment(request.getDepartment());
        return userRepository.save(user);
    }

    //  CHANGE PASSWORD BY EMAIL
    @Override
    public void changePasswordByEmail(String email, String oldPassword, String newPassword) {
        User user = getUserByEmail(email);
        
        //  Verify the old password matches before allowing a change
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new RuntimeException("Old password does not match!");
        }
        
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }


    //  DEACTIVATE USER
    @Override
    public void deactivateUser(String userId) {

        User user = getUserById(userId);
        user.setActive(!user.isActive());  //  FIXED

        userRepository.save(user);
    }

    //  DELETE USER (Hard Delete as per PDF)
    @Override
    public void deleteUser(String userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with ID: " + userId);
        }
        userRepository.deleteById(userId);
    }

    //  UPDATE USER ROLE
    @Override
    public void updateUserRole(String userId, String role) {
        User user = getUserById(userId);
        try {
            user.setRole(parseRole(role));
            userRepository.save(user);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid role provided: " + role + ". Allowed roles: STAFF, MANAGER, OFFICER, ADMIN");
        }
    }

    //  GET ALL USERS (SCOPED)
    @Override
    public List<User> getAllUsersScoped(String currentUserEmail) {
        // 1. Get the details of the person making the request
        User currentUser = getUserByEmail(currentUserEmail);

        // 2. If they are an ADMIN, they see everyone
        if (currentUser.getRole() == Role.ADMIN) {
            return userRepository.findAll();
        }

        // 3. If they are a MANAGER, they only see their own department
        if (currentUser.getRole() == Role.MANAGER) {
            return userRepository.findByDepartment(currentUser.getDepartment());
        }

        // 4. Otherwise, return empty
        return List.of();
    }

    @Override
    public List<String> getActiveUserEmails() {
        return userRepository.findActiveUserEmails().stream()
                .filter(email -> email != null && !email.isBlank())
                .map(email -> email.trim().toLowerCase())
                .distinct()
                .toList();
    }

    
    
    //10. for admin can create users
    @Override
    public AuthResponse createUserByAdmin(AdminCreateUserRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Email already exists: " + request.getEmail());
        }

        User user = new User();
        user.setUserId(UUID.randomUUID().toString());
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setDepartment(request.getDepartment());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setActive(true);

        //  Set custom role from Admin
        user.setRole(parseRole(request.getRole()));

        userRepository.save(user);

        String token = jwtUtil.generateToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);

        return new AuthResponse(token, refreshToken);
    }

    private Role parseRole(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Role is required. Allowed roles: STAFF, MANAGER, OFFICER, ADMIN");
        }
        return Role.valueOf(role.trim().toUpperCase());
    }
}
