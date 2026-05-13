package com.stockpro.payment.dto;

import com.stockpro.payment.entity.PaymentStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class PaymentResponse {
    private Long paymentId;
    private Long poId;
    private Long supplierId;
    private BigDecimal amount;
    private String currency;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String productDetails;
    private PaymentStatus status;
    private String razorpayKeyId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
