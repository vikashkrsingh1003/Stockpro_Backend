package com.stockpro.authservice.dto;

import lombok.Data;

@Data
public class ForgotPasswordResetRequest {
    private String email;
    private String otp;
    private String newPassword;
}
