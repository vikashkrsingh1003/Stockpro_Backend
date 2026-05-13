package com.stockpro.warehouse.service.impl;

import com.stockpro.warehouse.client.MovementClient;
import com.stockpro.warehouse.client.ProductClient;
import com.stockpro.warehouse.dto.StockIssueRequest;
import com.stockpro.warehouse.dto.StockWriteOffRequest;
import com.stockpro.warehouse.dto.StockReturnRequest;
import com.stockpro.warehouse.dto.StockTransferRequest;
import com.stockpro.warehouse.dto.StockAdjustmentRequest;
import com.stockpro.warehouse.dto.InventoryItemDTO;
import com.stockpro.warehouse.dto.ProductDTO;
import com.stockpro.warehouse.entity.MovementType;
import com.stockpro.warehouse.entity.StockLevel;
import com.stockpro.warehouse.entity.StockMovement;
import com.stockpro.warehouse.entity.Warehouse;
import com.stockpro.warehouse.publisher.StockEventPublisher;
import com.stockpro.warehouse.repository.StockLevelRepository;
import com.stockpro.warehouse.repository.WarehouseRepository;
import com.stockpro.warehouse.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class WarehouseServiceImpl implements WarehouseService {

    private final WarehouseRepository warehouseRepo;
    private final StockLevelRepository stockRepo;
    private final MovementClient movementClient;   // 🔥 Feign → stockmovement-service
    private final ProductClient productClient;
    private final StockEventPublisher stockEventPublisher; // 📡 RabbitMQ Publisher (non-critical)

    @Value("${stockpro.alert.low-stock-threshold:20}")
    private int lowStockThreshold;
    
    private String getCurrentUser() {
        try {
            return SecurityContextHolder.getContext()
                   .getAuthentication().getName();
        } catch (Exception e) {
            return "SYSTEM";
        }
    }



    //  Create warehouse
    @Override
    public Warehouse createWarehouse(Warehouse warehouse) {
        warehouse.setIsActive(true);
        warehouse.setUsedCapacity(0); // 🔥 Initialize at 0
        return warehouseRepo.save(warehouse);
    }

    //  Get all warehouses
    @Override
    public List<Warehouse> getAllWarehouses() {
        return warehouseRepo.findAll();
    }
    
 //  Get single warehouse by ID
    @Override
    public Warehouse getWarehouseById(Long id) {
        return warehouseRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Warehouse not found: " + id));
    }

    //  Update warehouse fields (name, location, address, phone, capacity)
    @Override
    public Warehouse updateWarehouse(Long id, Warehouse updated) {
        Warehouse existing = getWarehouseById(id);
        existing.setName(updated.getName());
        existing.setLocation(updated.getLocation());
        existing.setAddress(updated.getAddress());
        existing.setPhone(updated.getPhone());
        if (updated.getCapacity() != null) {
            existing.setCapacity(updated.getCapacity());
        }
        return warehouseRepo.save(existing);
    }

    //  Activate or deactivate a warehouse
    @Override
    public void setWarehouseActive(Long id, boolean active) {
        Warehouse warehouse = getWarehouseById(id);
        warehouse.setIsActive(active);
        warehouseRepo.save(warehouse);
    }

    //  Assign a manager to a warehouse
    @Override
    public void assignManager(Long warehouseId, Long managerId) {
        Warehouse warehouse = getWarehouseById(warehouseId);
        warehouse.setManagerId(managerId);
        warehouseRepo.save(warehouse);
    }


    //  Get stock
    @Override
    public StockLevel getStock(Long warehouseId, Long productId) {
        return stockRepo.findByWarehouseId(warehouseId).stream()
                .filter(s -> s.getProductId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Stock not found"));
    }

    //  Update stock
    @Override
    public StockLevel updateStock(Long warehouseId, Long productId, int quantity, String reason) {

        //  0. Validate Product via Product Service (Feign)
         try {
             productClient.getProductById(productId);
         } catch (Exception e) {
             throw new RuntimeException("Invalid Product ID: " + productId + " (Product not found) - Details: " + e.getMessage());
         } 

        // 1. Fetch Warehouse
        Warehouse warehouse = warehouseRepo.findById(warehouseId)
                .orElseThrow(() -> new RuntimeException("Warehouse not found"));

        // 2. Fetch existing stock (Handle duplicates gracefully if any exist)
        StockLevel stock = stockRepo.findByWarehouseId(warehouseId).stream()
                .filter(s -> s.getProductId().equals(productId))
                .findFirst()
                .orElse(new StockLevel());

        int oldQuantity = (stock.getQuantity() != null) ? stock.getQuantity() : 0;
        int delta = quantity - oldQuantity;

        // 3. Capacity Validation
        int currentUsedCapacity = (warehouse.getUsedCapacity() != null) ? warehouse.getUsedCapacity() : 0;
        int maxCapacity = (warehouse.getCapacity() != null) ? warehouse.getCapacity() : Integer.MAX_VALUE;
        if (currentUsedCapacity + delta > maxCapacity) {
            throw new RuntimeException("Warehouse capacity exceeded! 🛑 Max: " + maxCapacity);
        }

        // 4. Update Stock
        stock.setWarehouseId(warehouseId);
        stock.setProductId(productId);
        stock.setQuantity(quantity);
        stock.setReservedQuantity(0);
        stock.setLastUpdated(LocalDateTime.now());

        // 5. Update Warehouse Capacity
        warehouse.setUsedCapacity(currentUsedCapacity + delta);
        warehouseRepo.save(warehouse);

        // 6. Record Movement
        MovementType movementType = (delta >= 0) ? MovementType.IN : MovementType.OUT;
        recordMovement(warehouseId, productId, Math.abs(delta), movementType, reason); // 🔥 Using dynamic reason

        StockLevel savedStock = stockRepo.save(stock);

        //  Step 1: Existing Feign sync to product-service (unchanged)
        publishStockUpdateEvent(productId);

        //  Step 2: RabbitMQ event for analytics-service (non-blocking)
        stockEventPublisher.publishStockMovement(
                productId, warehouseId, Math.abs(delta),
                movementType.name(), reason
        );

        checkStockAlertThresholds(savedStock);

        return savedStock;
    }

    @Override
    public StockLevel adjustStock(StockAdjustmentRequest request) {
        String type = normalizeAdjustmentType(request.getAdjustmentType());
        int quantity = request.getQuantity() == null ? 0 : request.getQuantity();
        if (!"ADJUSTMENT".equals(type) && quantity <= 0) {
            throw new RuntimeException("Quantity must be at least 1 for " + type);
        }

        StockLevel stock = stockRepo.findByWarehouseIdAndProductId(request.getWarehouseId(), request.getProductId())
                .orElseGet(() -> {
                    if (isDeduction(type)) {
                        throw new RuntimeException("Stock not found for warehouse " + request.getWarehouseId() + " / product " + request.getProductId());
                    }
                    StockLevel created = new StockLevel();
                    created.setWarehouseId(request.getWarehouseId());
                    created.setProductId(request.getProductId());
                    created.setQuantity(0);
                    created.setReservedQuantity(0);
                    return created;
                });

        int currentQuantity = stock.getQuantity() == null ? 0 : stock.getQuantity();
        int newQuantity = switch (type) {
            case "IN", "RETURN" -> currentQuantity + quantity;
            case "OUT", "ISSUE", "WRITE_OFF" -> currentQuantity - quantity;
            case "ADJUSTMENT" -> quantity;
            default -> throw new RuntimeException("Invalid adjustment type: " + type);
        };

        if (newQuantity < 0) {
            throw new RuntimeException("Insufficient stock. Current: " + currentQuantity + ", requested: " + quantity);
        }

        String reason = buildAdjustmentReason(type, request.getReason());

        try {
            productClient.getProductById(request.getProductId());
        } catch (Exception e) {
            throw new RuntimeException("Invalid Product ID: " + request.getProductId() + " (Product not found) - Details: " + e.getMessage());
        }

        Warehouse warehouse = warehouseRepo.findById(request.getWarehouseId())
                .orElseThrow(() -> new RuntimeException("Warehouse not found"));

        int delta = newQuantity - currentQuantity;
        int currentUsedCapacity = warehouse.getUsedCapacity() != null ? warehouse.getUsedCapacity() : 0;
        int maxCapacity = warehouse.getCapacity() != null ? warehouse.getCapacity() : Integer.MAX_VALUE;
        if (currentUsedCapacity + delta > maxCapacity) {
            throw new RuntimeException("Warehouse capacity exceeded! Max: " + maxCapacity);
        }

        stock.setQuantity(newQuantity);
        stock.setLastUpdated(LocalDateTime.now());
        warehouse.setUsedCapacity(Math.max(0, currentUsedCapacity + delta));
        warehouseRepo.save(warehouse);
        StockLevel saved = stockRepo.save(stock);

        MovementType movementType = MovementType.valueOf(type);
        recordMovement(request.getWarehouseId(), request.getProductId(), Math.abs(delta), movementType, reason);
        publishStockUpdateEvent(request.getProductId());

        stockEventPublisher.publishStockMovement(
                request.getProductId(),
                request.getWarehouseId(),
                Math.abs(delta),
                movementType.name(),
                reason
        );

        checkStockAlertThresholds(saved);

        return saved;
    }

    //  NEW: Incremental Stock Update for Purchase Receipts
    @Override
    public StockLevel addStock(Long warehouseId, Long productId, int delta, String reason) {
        // 1. Fetch current stock (or create new)
        StockLevel stock = stockRepo.findByWarehouseId(warehouseId).stream()
                .filter(s -> s.getProductId().equals(productId))
                .findFirst()
                .orElseGet(() -> {
                    StockLevel s = new StockLevel();
                    s.setWarehouseId(warehouseId);
                    s.setProductId(productId);
                    s.setQuantity(0);
                    s.setReservedQuantity(0);
                    return s;
                });

        int newTotal = stock.getQuantity() + delta;
        
        // 2. Reuse updateStock logic by calling it with the NEW total
        // This ensures Capacity validation and Movement recording are handled correctly!
        return updateStock(warehouseId, productId, newTotal, reason);
    }

    //  Feign: Sync total global stock to Product Service
    private void publishStockUpdateEvent(Long productId) {
        try {
            int totalStock = stockRepo.findByProductId(productId)
                    .stream().mapToInt(StockLevel::getQuantity).sum();
            productClient.updateTotalStock(productId, totalStock);
            System.out.println(" [Feign] Stock synced for Product " + productId + " -> " + totalStock + " units");
        } catch (Exception e) {
            System.err.println(" Could not sync stock to Product Service: " + e.getMessage());
        }
    }

    //  Delete orphan stock entry (admin cleanup)
    @Override
    public void deleteStockEntry(Long warehouseId, Long productId) {
        StockLevel stock = stockRepo.findByWarehouseId(warehouseId).stream()
                .filter(s -> s.getProductId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Stock entry not found for warehouse " + warehouseId + " / product " + productId));

        // Correct warehouse usedCapacity
        Warehouse warehouse = warehouseRepo.findById(warehouseId)
                .orElseThrow(() -> new RuntimeException("Warehouse not found"));
        int currentUsed = (warehouse.getUsedCapacity() != null) ? warehouse.getUsedCapacity() : 0;
        int stockQty   = (stock.getQuantity() != null) ? stock.getQuantity() : 0;
        warehouse.setUsedCapacity(Math.max(0, currentUsed - stockQty));
        warehouseRepo.save(warehouse);

        stockRepo.delete(stock);
        System.out.println(" Stock entry deleted: warehouse=" + warehouseId + ", product=" + productId);
    }
    
    //updatethreshold
    @Override
    public void updateStockThreshold(Long warehouseId, Long productId, int threshold) {
        StockLevel stock = stockRepo.findByWarehouseIdAndProductId(warehouseId, productId)
                .orElseThrow(() -> new RuntimeException("Stock entry not found"));
        stock.setMinThreshold(threshold);
        stockRepo.save(stock);
    }


    //  Reserve stock
    @Override
    public void reserveStock(Long warehouseId, Long productId, int qty) {

        StockLevel stock = getStock(warehouseId, productId);

        if (stock.getAvailableQuantity() < qty) {
            throw new RuntimeException("Not enough available stock");
        }

        stock.setReservedQuantity(stock.getReservedQuantity() + qty);
        stockRepo.save(stock);
    }

    //  Release stock
    @Override
    public void releaseReservation(Long warehouseId, Long productId, int qty) {

        StockLevel stock = getStock(warehouseId, productId);

        stock.setReservedQuantity(stock.getReservedQuantity() - qty);
        stockRepo.save(stock);
    }
    
    
    @Override
    public List<StockLevel> getLowStockReport(Long warehouseId) {

        List<StockLevel> allStock = stockRepo.findByWarehouseId(warehouseId);

        return allStock.stream()
                .filter(StockLevel::isLowStock)
                .toList();
    }

    @Override
    public List<InventoryItemDTO> getWarehouseInventory(Long warehouseId) {
        return stockRepo.findByWarehouseId(warehouseId).stream()
                .map(this::toInventoryItem)
                .toList();
    }
    @Override
    public StockTransferRequest applyTransfer(StockTransferRequest request) {
        Long productId = request.getProductId();
        Long fromWarehouse = request.getFromWarehouse();
        Long toWarehouse = request.getToWarehouse();
        int qty = request.getQty();

        StockLevel source = getStock(fromWarehouse, productId);
        if (source.getQuantity() < qty) {
            throw new RuntimeException("Insufficient stock in source warehouse!");
        }

        StockLevel target = stockRepo.findByWarehouseId(toWarehouse).stream()
                .filter(s -> s.getProductId().equals(productId))
                .findFirst()
                .orElseGet(() -> {
                    StockLevel newStock = new StockLevel();
                    newStock.setWarehouseId(toWarehouse);
                    newStock.setProductId(productId);
                    newStock.setQuantity(0);
                    newStock.setReservedQuantity(0);
                    newStock.setMinThreshold(5);
                    return newStock;
                });

        Warehouse sourceW = warehouseRepo.findById(fromWarehouse).orElseThrow();
        Warehouse targetW = warehouseRepo.findById(toWarehouse).orElseThrow();

        int targetUsedCapacity = (targetW.getUsedCapacity() != null) ? targetW.getUsedCapacity() : 0;
        int targetMaxCapacity = (targetW.getCapacity() != null) ? targetW.getCapacity() : Integer.MAX_VALUE;
        if (targetUsedCapacity + qty > targetMaxCapacity) {
            throw new RuntimeException("Target Warehouse capacity exceeded!  Max: " + targetMaxCapacity);
        }

        source.setQuantity(source.getQuantity() - qty);
        target.setQuantity(target.getQuantity() + qty);
        source.setLastUpdated(LocalDateTime.now());
        target.setLastUpdated(LocalDateTime.now());

        int sourceUsedCapacity = (sourceW.getUsedCapacity() != null) ? sourceW.getUsedCapacity() : 0;
        sourceW.setUsedCapacity(sourceUsedCapacity - qty);
        targetW.setUsedCapacity(targetUsedCapacity + qty);

        warehouseRepo.save(sourceW);
        warehouseRepo.save(targetW);
        stockRepo.save(source);
        stockRepo.save(target);

        return request;
    }
    
    //  Records movement via Feign → stockmovement-service
    // Warehouse service no longer stores movements itself
    private void recordMovement(Long warehouseId, Long productId, int quantity, MovementType type, String reason) {
        StockMovement movement = new StockMovement();
        movement.setWarehouseId(warehouseId);
        movement.setProductId(productId);
        movement.setQuantity(quantity);
        movement.setType(type);
        movement.setReason(reason);
        movement.setPerformedBy(getCurrentUser());
        movement.setTimestamp(LocalDateTime.now());

        try {
            movementClient.record(movement);
        } catch (Exception e) {
            // Log but don't fail — movement recording is non-critical
            System.err.println(" Could not record movement to stock-movement-service: " + e.getMessage());
        }
    }

    private String normalizeAdjustmentType(String type) {
        if (type == null || type.isBlank()) {
            throw new RuntimeException("Adjustment type is required");
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if (!List.of("IN", "OUT", "RETURN", "ISSUE", "WRITE_OFF", "ADJUSTMENT").contains(normalized)) {
            throw new RuntimeException("Invalid adjustment type. Use IN, OUT, RETURN, ISSUE, WRITE_OFF, or ADJUSTMENT");
        }
        return normalized;
    }

    private boolean isDeduction(String type) {
        return List.of("OUT", "ISSUE", "WRITE_OFF").contains(type);
    }

    private String buildAdjustmentReason(String type, String reason) {
        String detail = reason == null || reason.isBlank() ? "No reason recorded" : reason.trim();
        return type + " | " + detail;
    }

    private InventoryItemDTO toInventoryItem(StockLevel stock) {
        ProductDTO product = null;
        try {
            product = productClient.getProductById(stock.getProductId());
        } catch (Exception ignored) {
            // Keep inventory usable even if product-service is temporarily unavailable.
        }

        return InventoryItemDTO.builder()
                .stockId(stock.getStockId())
                .warehouseId(stock.getWarehouseId())
                .productId(stock.getProductId())
                .productName(product == null ? "Product #" + stock.getProductId() : product.getName())
                .sku(product == null ? "PROD-" + stock.getProductId() : product.getSku())
                .category(product == null ? null : product.getCategory())
                .brand(product == null ? null : product.getBrand())
                .quantity(stock.getQuantity())
                .reservedQuantity(stock.getReservedQuantity())
                .availableQuantity(stock.getAvailableQuantity())
                .minThreshold(stock.getMinThreshold())
                .maxStockLevel(stock.getMaxStockLevel())
                .location(stock.getLocation())
                .lastUpdated(stock.getLastUpdated())
                .build();
    }

    private void checkStockAlertThresholds(StockLevel stock) {
        try {
            int quantity = stock.getQuantity() == null ? 0 : stock.getQuantity();
            int maxLevel = stock.getMaxStockLevel() == null ? Integer.MAX_VALUE : stock.getMaxStockLevel();

            if (quantity < lowStockThreshold) {
                stockEventPublisher.publishStockAlert(
                        stock.getProductId(),
                        stock.getWarehouseId(),
                        quantity,
                        com.stockpro.warehouse.config.RabbitMQConfig.STOCK_LOW_ROUTING_KEY
                );
            } else if (quantity > maxLevel) {
                stockEventPublisher.publishStockAlert(
                        stock.getProductId(),
                        stock.getWarehouseId(),
                        quantity,
                        com.stockpro.warehouse.config.RabbitMQConfig.STOCK_HIGH_ROUTING_KEY
                );
            }
        } catch (Exception ignored) {
            // Alert failures should not block stock operations.
        }
    }

    // ─────────────────────────────────────────────────────────────
    // STOCK ISSUE — Consumption (Sales / Production / Internal Use)
    // PDF §2.2: "Record stock issues/consumption for production, sales, or internal use"
    // ─────────────────────────────────────────────────────────────
    @Override
    public StockLevel issueStock(StockIssueRequest request) {

        // 1. Validate issue type
        String issueType = request.getIssueType().toUpperCase();
        if (!issueType.equals("SALES") && !issueType.equals("PRODUCTION") && !issueType.equals("INTERNAL_USE")) {
            throw new RuntimeException("Invalid issue type. Must be: SALES, PRODUCTION, or INTERNAL_USE");
        }

        // 2. Fetch stock record
        StockLevel stock = getStock(request.getWarehouseId(), request.getProductId());

        // 3. Check available quantity (cannot issue reserved stock)
        int available = stock.getAvailableQuantity();
        if (request.getQuantity() > available) {
            throw new RuntimeException(
                "Insufficient available stock. Requested: " + request.getQuantity() +
                ", Available: " + available + " (Total: " + stock.getQuantity() +
                ", Reserved: " + stock.getReservedQuantity() + ")"
            );
        }

        // 4. Deduct quantity
        int newQty = stock.getQuantity() - request.getQuantity();
        stock.setQuantity(newQty);
        stock.setLastUpdated(LocalDateTime.now());

        // 5. Update warehouse used capacity
        Warehouse warehouse = warehouseRepo.findById(request.getWarehouseId())
                .orElseThrow(() -> new RuntimeException("Warehouse not found"));
        int currentUsed = warehouse.getUsedCapacity() != null ? warehouse.getUsedCapacity() : 0;
        warehouse.setUsedCapacity(Math.max(0, currentUsed - request.getQuantity()));
        warehouseRepo.save(warehouse);

        // 6. Save updated stock
        StockLevel saved = stockRepo.save(stock);

        // 7. Build reason string: e.g. "ISSUE:SALES | Sales Order #1234"
        String reason = "ISSUE:" + issueType +
                (request.getNotes() != null ? " | " + request.getNotes() : "");

        // 8. Record movement as ISSUE type
        recordMovement(request.getWarehouseId(), request.getProductId(),
                request.getQuantity(), MovementType.ISSUE, reason);

        // 9. Feign sync to product-service
        publishStockUpdateEvent(request.getProductId());

        // 10. RabbitMQ event to analytics-service
        stockEventPublisher.publishStockMovement(
                request.getProductId(), request.getWarehouseId(),
                request.getQuantity(), "ISSUE", reason
        );

        checkStockAlertThresholds(saved);

        return saved;
    }

    // ─────────────────────────────────────────────────────────────
    // STOCK WRITE-OFF — Damaged / Expired goods (PDF §2.6)
    // ─────────────────────────────────────────────────────────────
    @Override
    public StockLevel writeOffStock(StockWriteOffRequest request) {

        StockLevel stock = getStock(request.getWarehouseId(), request.getProductId());

        if (request.getQuantity() > stock.getQuantity()) {
            throw new RuntimeException(
                "Cannot write off more than available stock. Available: " + stock.getQuantity());
        }

        // Deduct stock
        stock.setQuantity(stock.getQuantity() - request.getQuantity());
        stock.setLastUpdated(LocalDateTime.now());
        StockLevel saved = stockRepo.save(stock);

        // Update warehouse capacity
        Warehouse warehouse = warehouseRepo.findById(request.getWarehouseId())
                .orElseThrow(() -> new RuntimeException("Warehouse not found"));
        int currentUsed = warehouse.getUsedCapacity() != null ? warehouse.getUsedCapacity() : 0;
        warehouse.setUsedCapacity(Math.max(0, currentUsed - request.getQuantity()));
        warehouseRepo.save(warehouse);

        // Build reason string
        String reason = "WRITE_OFF:" + request.getWriteOffReason() +
                (request.getNotes() != null ? " | " + request.getNotes() : "");

        // Record movement as WRITE_OFF
        recordMovement(request.getWarehouseId(), request.getProductId(),
                request.getQuantity(), MovementType.WRITE_OFF, reason);

        // Feign sync
        publishStockUpdateEvent(request.getProductId());

        // RabbitMQ event
        stockEventPublisher.publishStockMovement(
                request.getProductId(), request.getWarehouseId(),
                request.getQuantity(), "WRITE_OFF", reason);

        return saved;
    }

    // ─────────────────────────────────────────────────────────────
    // STOCK RETURN — Supplier or Customer Return (PDF §2.6)
    // ─────────────────────────────────────────────────────────────
    @Override
    public StockLevel returnStock(StockReturnRequest request) {

        String returnType = request.getReturnType().toUpperCase();
        if (!returnType.equals("SUPPLIER_RETURN") && !returnType.equals("CUSTOMER_RETURN")) {
            throw new RuntimeException("Invalid return type. Must be: SUPPLIER_RETURN or CUSTOMER_RETURN");
        }

        StockLevel stock = getStock(request.getWarehouseId(), request.getProductId());

        // Returns ADD back to stock
        stock.setQuantity(stock.getQuantity() + request.getQuantity());
        stock.setLastUpdated(LocalDateTime.now());
        StockLevel saved = stockRepo.save(stock);

        // Update warehouse capacity
        Warehouse warehouse = warehouseRepo.findById(request.getWarehouseId())
                .orElseThrow(() -> new RuntimeException("Warehouse not found"));
        int currentUsed = warehouse.getUsedCapacity() != null ? warehouse.getUsedCapacity() : 0;
        warehouse.setUsedCapacity(currentUsed + request.getQuantity());
        warehouseRepo.save(warehouse);

        // Build reason string
        String reason = "RETURN:" + returnType +
                (request.getReferenceNumber() != null ? " | Ref: " + request.getReferenceNumber() : "") +
                (request.getNotes() != null ? " | " + request.getNotes() : "");

        // Record movement as RETURN
        recordMovement(request.getWarehouseId(), request.getProductId(),
                request.getQuantity(), MovementType.RETURN, reason);

        // Feign sync
        publishStockUpdateEvent(request.getProductId());

        // RabbitMQ event
        stockEventPublisher.publishStockMovement(
                request.getProductId(), request.getWarehouseId(),
                request.getQuantity(), "RETURN", reason);

        return saved;
    }

}
