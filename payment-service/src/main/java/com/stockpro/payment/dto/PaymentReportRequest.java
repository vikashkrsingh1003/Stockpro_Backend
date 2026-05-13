package com.stockpro.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PaymentReportRequest {

    @NotNull(message = "poId is required")
    private Long poId;

    @NotNull(message = "supplierId is required")
    private Long supplierId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than zero")
    private BigDecimal amount;

    private String currency = "INR";

    private String productDetails;
}
