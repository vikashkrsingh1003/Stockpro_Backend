package com.stockpro.alert.dto;

import lombok.Data;

@Data
public class PurchaseOrderEvent {
    private Long poId;
    private Long warehouseId;
    private String referenceNumber;
}