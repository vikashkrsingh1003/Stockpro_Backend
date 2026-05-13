package com.stockpro.warehouse.controller;

import com.stockpro.warehouse.dto.StockIssueRequest;
import com.stockpro.warehouse.dto.StockWriteOffRequest;
import com.stockpro.warehouse.dto.StockReturnRequest;
import com.stockpro.warehouse.dto.StockTransferRequest;
import com.stockpro.warehouse.dto.StockAdjustmentRequest;
import com.stockpro.warehouse.dto.InventoryItemDTO;
import com.stockpro.warehouse.entity.StockLevel;
import com.stockpro.warehouse.entity.Warehouse;
import com.stockpro.warehouse.service.WarehouseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService service;

    @Value("${stockpro.internal.service-token:stockpro-internal-token}")
    private String internalServiceToken;

    // Create Warehouse
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @PostMapping
    public Warehouse createWarehouse(@RequestBody Warehouse warehouse) {
        return service.createWarehouse(warehouse);
    }

    // Get All Warehouses
    @GetMapping
    public List<Warehouse> getAllWarehouses() {
        return service.getAllWarehouses();
    }

    // Get single warehouse
    @GetMapping("/{id}")
    public Warehouse getById(@PathVariable("id") Long id) {
        return service.getWarehouseById(id);
    }

    // Update warehouse
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @PutMapping("/{id}")
    public Warehouse updateWarehouse(@PathVariable("id") Long id, @RequestBody Warehouse warehouse) {
        return service.updateWarehouse(id, warehouse);
    }

    // Activate / Deactivate warehouse
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @PutMapping("/{id}/status")
    public String setStatus(@PathVariable("id") Long id, @RequestParam("active") boolean active) {
        service.setWarehouseActive(id, active);
        return active ? "Warehouse Activated" : "Warehouse Deactivated";
    }

    // Assign Manager
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @PutMapping("/{id}/manager")
    public String assignManager(@PathVariable("id") Long id, @RequestParam("managerId") Long managerId) {
        service.assignManager(id, managerId);
        return "Manager assigned";
    }

    // Get Stock of a Product in Warehouse
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    @GetMapping("/stock")
    public StockLevel getStock(@RequestParam("warehouseId") Long warehouseId,
                               @RequestParam("productId") Long productId) {
        return service.getStock(warehouseId, productId);
    }

    // Manual Stock Update (sets absolute quantity)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    @PostMapping("/{warehouseId}/stock")
    public StockLevel updateStock(@PathVariable("warehouseId") Long warehouseId,
                                  @RequestParam("productId") Long productId,
                                  @RequestParam("quantity") int quantity,
                                  @RequestParam("reason") String reason) {
        return service.updateStock(warehouseId, productId, quantity, reason);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    @PostMapping("/stock/adjustment")
    public StockLevel adjustStock(@Valid @RequestBody StockAdjustmentRequest request) {
        return service.adjustStock(request);
    }

    // Incremental Add (used by Purchase Service via Feign)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    @PostMapping("/{warehouseId}/stock/add")
    public StockLevel addStock(@PathVariable("warehouseId") Long warehouseId,
                               @RequestParam("productId") Long productId,
                               @RequestParam("delta") int delta,
                               @RequestParam("reason") String reason) {
        return service.addStock(warehouseId, productId, delta, reason);
    }

    // Delete orphan stock entry (Admin cleanup)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @DeleteMapping("/{warehouseId}/stock")
    public String deleteStockEntry(@PathVariable("warehouseId") Long warehouseId,
                                   @RequestParam("productId") Long productId) {
        service.deleteStockEntry(warehouseId, productId);
        return "Stock entry deleted";
    }

    // Update Min Threshold
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @PutMapping("/{warehouseId}/stock/threshold")
    public String updateThreshold(@PathVariable("warehouseId") Long warehouseId,
                                   @RequestParam("productId") Long productId,
                                   @RequestParam("threshold") int threshold) {
        service.updateStockThreshold(warehouseId, productId, threshold);
        return "Threshold updated";
    }

    // Reserve Stock
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @PostMapping("/{warehouseId}/reserve")
    public String reserveStock(@PathVariable("warehouseId") Long warehouseId,
                               @RequestParam("productId") Long productId,
                               @RequestParam("qty") int qty) {
        service.reserveStock(warehouseId, productId, qty);
        return "Stock Reserved";
    }

    // Release Stock
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @PostMapping("/{warehouseId}/release")
    public String releaseStock(@PathVariable("warehouseId") Long warehouseId,
                               @RequestParam("productId") Long productId,
                               @RequestParam("qty") int qty) {
        service.releaseReservation(warehouseId, productId, qty);
        return "Stock Released";
    }

    @PostMapping("/internal/transfer")
    public StockTransferRequest applyTransfer(@RequestHeader("X-Internal-Service-Token") String token,
                                              @Valid @RequestBody StockTransferRequest request) {
        if (!internalServiceToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal service token");
        }
        return service.applyTransfer(request);
    }

    // Low Stock Report
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @GetMapping("/{warehouseId}/low-stock")
    public List<StockLevel> getLowStock(@PathVariable("warehouseId") Long warehouseId) {
        return service.getLowStockReport(warehouseId);
    }

    // Full Inventory of a Warehouse
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    @GetMapping("/{id}/inventory")
    public List<InventoryItemDTO> getInventory(@PathVariable("id") Long id) {
        return service.getWarehouseInventory(id);
    }

    // Stock Issue — Sales / Production / Internal Use (PDF §2.2)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    @PostMapping("/stock/issue")
    public StockLevel issueStock(@Valid @RequestBody StockIssueRequest request) {
        return service.issueStock(request);
    }

    // Stock Write-Off — Damaged or Expired goods (PDF §2.6)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    @PostMapping("/stock/write-off")
    public StockLevel writeOffStock(@Valid @RequestBody StockWriteOffRequest request) {
        return service.writeOffStock(request);
    }

    // Stock Return — Supplier or Customer return (PDF §2.6)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    @PostMapping("/stock/return")
    public StockLevel returnStock(@Valid @RequestBody StockReturnRequest request) {
        return service.returnStock(request);
    }
}
