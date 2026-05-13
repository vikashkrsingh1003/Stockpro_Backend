package com.stockpro.payment.exception;

import org.springframework.http.HttpStatus;

public class InvalidPaymentSignatureException extends PaymentException {
    public InvalidPaymentSignatureException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
