package com.stockpro.warehouse.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "warehouses")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long warehouseId;

    @Column(nullable = false, unique = true)
    private String name;

    private String location;
    private String address;

    private Long managerId;

    private Integer capacity;
    private Integer usedCapacity;

    private Boolean isActive = true;

    private String phone;

    private LocalDateTime createdAt = LocalDateTime.now();
}