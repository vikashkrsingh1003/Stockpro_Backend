package com.stockpro.purchase.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentStatusUpdateRequest {
    private String paymentStatus;
    private String razorpayOrderId;
    private String razorpayPaymentId;
}
