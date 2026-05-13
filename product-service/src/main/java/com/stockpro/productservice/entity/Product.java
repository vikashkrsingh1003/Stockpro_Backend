package com.stockpro.productservice.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long productId;

    @Column(unique = true, nullable = false)
    private String sku;

    @Column(nullable = false)
    private String name;

    private String description;

    private String category;

    private String brand;

    private String unitOfMeasure; // e.g., kg, piece, liter

    private Double costPrice;

    private Double sellingPrice;

    private Integer reorderLevel;

    private Integer maxStockLevel;

    private Integer leadTimeDays;
    
    @Column(length = 1000)
    private String imageUrl;

    private String barcode;
    
    private Integer totalStock = 0; //  Added for RabbitMQ syncing

    private Boolean isActive = true;
}