package com.stockpro.purchase.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class PaymentReportRequest {
    private Long poId;
    private Long supplierId;
    private BigDecimal amount;
    private String currency;
    private String productDetails;
}
