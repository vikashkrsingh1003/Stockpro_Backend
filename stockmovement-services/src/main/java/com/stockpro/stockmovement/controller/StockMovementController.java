package com.stockpro.stockmovement.controller;

import com.stockpro.stockmovement.dto.StockTransferRequest;
import com.stockpro.stockmovement.entity.MovementType;
import com.stockpro.stockmovement.entity.StockMovement;
import com.stockpro.stockmovement.service.StockMovementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/movements")
@RequiredArgsConstructor
public class StockMovementController {

    private final StockMovementService movementService;

    // INTERNAL: Called by warehouse-service via Feign to record movements
    // No @PreAuthorize — internal service call, not exposed to users via gateway
    @PostMapping("/record")
    public StockMovement record(@RequestBody StockMovement movement) {
        return movementService.record(movement);
    }

    @PostMapping("/transfer")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public Map<String, String> transfer(@Valid @RequestBody StockTransferRequest request, Authentication authentication) {
        String performedBy = authentication == null ? "SYSTEM" : authentication.getName();
        movementService.transfer(request, performedBy);
        return Map.of("message", "Product transfer successful");
    }

    // GET all movements with optional filters (warehouseId, productId, type, from, to)
    // Used by frontend MovementsPage
    @GetMapping
    public List<StockMovement> getFiltered(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) MovementType type,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        LocalDateTime fromDt = parseDateTime(from, false);
        LocalDateTime toDt   = parseDateTime(to, true);
        return movementService.getFiltered(warehouseId, productId, type, fromDt, toDt);
    }

    private LocalDateTime parseDateTime(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
            LocalDate date = LocalDate.parse(value);
            return endOfDay ? date.atTime(23, 59, 59) : date.atStartOfDay();
        }
    }

    // GET all movements for a specific warehouse
    @GetMapping("/warehouse/{warehouseId}")
    public List<StockMovement> getByWarehouse(@PathVariable Long warehouseId) {
        return movementService.getByWarehouse(warehouseId);
    }

    // GET all movements for a specific product (across all warehouses)
    @GetMapping("/product/{productId}")
    public List<StockMovement> getByProduct(@PathVariable Long productId) {
        return movementService.getByProduct(productId);
    }

    // GET movements for a product in a specific warehouse
    @GetMapping("/warehouse/{warehouseId}/product/{productId}")
    public List<StockMovement> getByWarehouseAndProduct(@PathVariable Long warehouseId,
                                                        @PathVariable Long productId) {
        return movementService.getByWarehouseAndProduct(warehouseId, productId);
    }
}
