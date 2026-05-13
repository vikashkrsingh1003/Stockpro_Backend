package com.stockpro.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SupplierSpendDTO {
    private Long supplierId;
    private String supplierName;
    private Double totalSpend;
    private Integer totalOrdersReceived;
    private Double avgOrderValue;
    private String lastPurchaseDate;
}
