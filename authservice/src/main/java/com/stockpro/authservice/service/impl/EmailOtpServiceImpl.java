package com.stockpro.authservice.service.impl;

import com.stockpro.authservice.entities.EmailOtp;
import com.stockpro.authservice.repository.EmailOtpRepository;
import com.stockpro.authservice.service.EmailOtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailOtpServiceImpl implements EmailOtpService {

    private static final int MAX_ATTEMPTS = 5;

    private final EmailOtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    @Value("${stockpro.otp.expiry-minutes:10}")
    private int expiryMinutes;

    @Value("${stockpro.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    private final SecureRandom random = new SecureRandom();

    @Override
    @Transactional
    public void sendOtp(String email, String purpose) {
        String normalizedEmail = normalizeEmail(email);
        String otp = String.format("%06d", random.nextInt(1_000_000));
        LocalDateTime now = LocalDateTime.now();

        otpRepository.save(EmailOtp.builder()
                .email(normalizedEmail)
                .otpHash(passwordEncoder.encode(otp))
                .purpose(purpose)
                .createdAt(now)
                .expiresAt(now.plusMinutes(expiryMinutes))
                .attempts(0)
                .used(false)
                .build());

        sendEmail(normalizedEmail, purpose, otp);
    }

    @Override
    @Transactional
    public void verifyOtp(String email, String otp, String purpose) {
        String normalizedEmail = normalizeEmail(email);
        EmailOtp emailOtp = otpRepository.findTopByEmailAndPurposeAndUsedFalseOrderByCreatedAtDesc(normalizedEmail, purpose)
                .orElseThrow(() -> new RuntimeException("OTP not found. Please request a new OTP."));

        if (emailOtp.getExpiresAt().isBefore(LocalDateTime.now())) {
            emailOtp.setUsed(true);
            otpRepository.save(emailOtp);
            throw new RuntimeException("OTP expired. Please request a new OTP.");
        }

        if (emailOtp.getAttempts() >= MAX_ATTEMPTS) {
            emailOtp.setUsed(true);
            otpRepository.save(emailOtp);
            throw new RuntimeException("Too many wrong OTP attempts. Please request a new OTP.");
        }

        if (!passwordEncoder.matches(otp, emailOtp.getOtpHash())) {
            emailOtp.setAttempts(emailOtp.getAttempts() + 1);
            otpRepository.save(emailOtp);
            throw new RuntimeException("Invalid OTP. Please try again.");
        }

        emailOtp.setUsed(true);
        emailOtp.setVerifiedAt(LocalDateTime.now());
        otpRepository.save(emailOtp);
    }

    private void sendEmail(String email, String purpose, String otp) {
        if (!mailEnabled || fromEmail == null || fromEmail.isBlank()) {
            log.info("OTP for {} [{}]: {}", email, purpose, otp);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject(subjectFor(purpose));
            message.setText("Your StockPro OTP is: " + otp + "\n\nThis OTP is valid for " + expiryMinutes + " minutes.");
            mailSender.send(message);
        } catch (MailException ex) {
            log.warn("Unable to send OTP email to {}: {}", email, ex.getMessage());
            throw new RuntimeException("Unable to send OTP email. Please try again later.");
        }
    }

    private String subjectFor(String purpose) {
        return EmailOtpService.PASSWORD_RESET.equals(purpose)
                ? "StockPro Password Reset OTP"
                : "StockPro Registration OTP";
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new RuntimeException("Email is required.");
        }
        return email.trim().toLowerCase();
    }
}
