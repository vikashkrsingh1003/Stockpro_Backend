package com.stockpro.productservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductResponseDTO {

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
    private String imageUrl;
}
