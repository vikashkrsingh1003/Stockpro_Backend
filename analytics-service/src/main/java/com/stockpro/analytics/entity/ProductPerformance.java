package com.stockpro.analytics.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class ProductPerformance {
    @Id
    private Long productId; // Same ID as Product Service
    
    private String sku;
    private Double turnoverRate;      // (Cost of Goods Sold / Avg Inventory)
    private Integer daysInStock;      // How long the product has been sitting
    private String movementCategory;  // "TOP_MOVING", "SLOW_MOVING", "DEAD"
    private LocalDateTime lastCalculated;
}
