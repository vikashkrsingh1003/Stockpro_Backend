package com.stockpro.payment.exception;

import org.springframework.http.HttpStatus;

public class PaymentNotFoundException extends PaymentException {
    public PaymentNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
