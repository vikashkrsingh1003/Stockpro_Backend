package com.stockpro.warehouse.dto;

import lombok.Data;

@Data
public class ProductDTO {
    private Long productId;
    private String sku;
    private String name;
    private String category;
    private String brand;
    private Double sellingPrice;
    private Integer reorderLevel;
    private Boolean isActive;
    private Integer totalStock;
    private Double costPrice;
    private String barcode;
}
