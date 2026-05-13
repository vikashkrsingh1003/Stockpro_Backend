package com.stockpro.warehouse.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class InventoryItemDTO {
    private Long stockId;
    private Long warehouseId;
    private Long productId;
    private String productName;
    private String sku;
    private String category;
    private String brand;
    private Integer quantity;
    private Integer reservedQuantity;
    private Integer availableQuantity;
    private Integer minThreshold;
    private Integer maxStockLevel;
    private String location;
    private LocalDateTime lastUpdated;
}
