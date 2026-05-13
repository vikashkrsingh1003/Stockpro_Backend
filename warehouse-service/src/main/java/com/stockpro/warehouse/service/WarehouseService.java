package com.stockpro.warehouse.service;

import com.stockpro.warehouse.dto.StockIssueRequest;
import com.stockpro.warehouse.dto.StockWriteOffRequest;
import com.stockpro.warehouse.dto.StockReturnRequest;
import com.stockpro.warehouse.dto.StockTransferRequest;
import com.stockpro.warehouse.dto.StockAdjustmentRequest;
import com.stockpro.warehouse.dto.InventoryItemDTO;
import com.stockpro.warehouse.entity.Warehouse;
import com.stockpro.warehouse.entity.StockLevel;

import java.util.List;

public interface WarehouseService {

    // Warehouse
    Warehouse createWarehouse(Warehouse warehouse);
    List<Warehouse> getAllWarehouses();
    Warehouse getWarehouseById(Long id);
    Warehouse updateWarehouse(Long id, Warehouse updated);
    void setWarehouseActive(Long id, boolean active);
    void assignManager(Long warehouseId, Long managerId);

    // Stock
    StockLevel getStock(Long warehouseId, Long productId);
    StockLevel updateStock(Long warehouseId, Long productId, int quantity, String reason);
    StockLevel adjustStock(StockAdjustmentRequest request);
    StockLevel addStock(Long warehouseId, Long productId, int delta, String reason);
    void deleteStockEntry(Long warehouseId, Long productId);
    void updateStockThreshold(Long warehouseId, Long productId, int threshold);

    // Stock Issue — Consumption (Sales, Production, Internal Use)
    StockLevel issueStock(StockIssueRequest request);

    // Stock Write-Off — Damaged or Expired goods (PDF §2.6)
    StockLevel writeOffStock(StockWriteOffRequest request);

    // Stock Return — From Supplier or Customer (PDF §2.6)
    StockLevel returnStock(StockReturnRequest request);

    List<StockLevel> getLowStockReport(Long warehouseId);
    List<InventoryItemDTO> getWarehouseInventory(Long warehouseId);

    // Reservation
    void reserveStock(Long warehouseId, Long productId, int qty);
    void releaseReservation(Long warehouseId, Long productId, int qty);

    StockTransferRequest applyTransfer(StockTransferRequest request);
}
