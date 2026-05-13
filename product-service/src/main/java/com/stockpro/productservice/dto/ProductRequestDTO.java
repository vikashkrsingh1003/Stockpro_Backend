package com.stockpro.productservice.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ProductRequestDTO {

    @NotBlank
    private String sku;

    @NotBlank
    private String name;

    private String description;
    private String category;
    private String brand;
    private String unitOfMeasure;

    @NotNull
    private Double costPrice;

    @NotNull
    private Double sellingPrice;

    private Integer reorderLevel;
    private Integer maxStockLevel;
    private Integer leadTimeDays;

    private String barcode;
    private String imageUrl;
}