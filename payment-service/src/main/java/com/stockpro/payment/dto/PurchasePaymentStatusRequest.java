package com.stockpro.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PurchasePaymentStatusRequest {
    private String paymentStatus;
    private String razorpayOrderId;
    private String razorpayPaymentId;
}
