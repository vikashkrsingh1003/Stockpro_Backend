package com.stockpro.authservice.service;

public interface EmailOtpService {

    String REGISTRATION = "REGISTRATION";
    String PASSWORD_RESET = "PASSWORD_RESET";

    void sendOtp(String email, String purpose);

    void verifyOtp(String email, String otp, String purpose);
}
