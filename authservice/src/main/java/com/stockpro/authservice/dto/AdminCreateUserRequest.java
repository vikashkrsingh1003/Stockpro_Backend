package com.stockpro.authservice.dto;

import lombok.Data;

@Data
public class AdminCreateUserRequest {
    private String fullName;
    private String email;
    private String password;
    private String role; // ADMIN / MANAGER / OFFICER / STAFF
    private String phone;
    private String department;
}