package com.stockpro.warehouse.service.impl;

import com.stockpro.warehouse.client.MovementClient;
import com.stockpro.warehouse.client.ProductClient;
import com.stockpro.warehouse.dto.*;
import com.stockpro.warehouse.entity.StockLevel;
import com.stockpro.warehouse.entity.Warehouse;
import com.stockpro.warehouse.publisher.StockEventPublisher;
import com.stockpro.warehouse.repository.StockLevelRepository;
import com.stockpro.warehouse.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceImplTest {

    @Mock
    private WarehouseRepository warehouseRepo;

    @Mock
    private StockLevelRepository stockRepo;

    @Mock
    private MovementClient movementClient;

    @Mock
    private ProductClient productClient;

    @Mock
    private StockEventPublisher stockEventPublisher;

    @InjectMocks
    private WarehouseServiceImpl service;

    private Warehouse warehouse;
    private StockLevel stock;
    private ProductDTO product;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "lowStockThreshold", 5);

        warehouse = new Warehouse();
        warehouse.setWarehouseId(1L);
        warehouse.setName("Main");
        warehouse.setCapacity(100);
        warehouse.setUsedCapacity(10);
        warehouse.setIsActive(true);

        stock = new StockLevel();
        stock.setStockId(1L);
        stock.setWarehouseId(1L);
        stock.setProductId(2L);
        stock.setQuantity(10);
        stock.setReservedQuantity(2);
        stock.setMinThreshold(5);
        stock.setMaxStockLevel(100);

        product = new ProductDTO();
        product.setProductId(2L);
        product.setName("Phone");
        product.setSku("PHN-001");
        product.setCategory("Electronics");
        product.setBrand("Apple");
    }

    @Test
    void createWarehouse_success() {
        warehouse.setIsActive(false);
        warehouse.setUsedCapacity(50);
        when(warehouseRepo.save(warehouse)).thenReturn(warehouse);

        Warehouse saved = service.createWarehouse(warehouse);

        assertTrue(saved.getIsActive());
        assertEquals(0, saved.getUsedCapacity());
    }

    @Test
    void getAllWarehouses_success() {
        when(warehouseRepo.findAll()).thenReturn(List.of(warehouse));

        assertEquals(1, service.getAllWarehouses().size());
    }

    @Test
    void getWarehouseById_success() {
        when(warehouseRepo.findById(1L)).thenReturn(Optional.of(warehouse));

        assertEquals("Main", service.getWarehouseById(1L).getName());
    }

    @Test
    void getWarehouseById_notFound() {
        when(warehouseRepo.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.getWarehouseById(1L));
    }

    @Test
    void updateWarehouse_success() {
        Warehouse updated = new Warehouse();
        updated.setName("Updated");
        updated.setLocation("Mumbai");
        updated.setAddress("Street");
        updated.setPhone("111");
        updated.setCapacity(200);
        when(warehouseRepo.findById(1L)).thenReturn(Optional.of(warehouse));
        when(warehouseRepo.save(warehouse)).thenReturn(warehouse);

        Warehouse saved = service.updateWarehouse(1L, updated);

        assertEquals("Updated", saved.getName());
        assertEquals(200, saved.getCapacity());
    }

    @Test
    void setWarehouseActive_success() {
        when(warehouseRepo.findById(1L)).thenReturn(Optional.of(warehouse));

        service.setWarehouseActive(1L, false);

        assertFalse(warehouse.getIsActive());
        verify(warehouseRepo).save(warehouse);
    }

    @Test
    void assignManager_success() {
        when(warehouseRepo.findById(1L)).thenReturn(Optional.of(warehouse));

        service.assignManager(1L, 99L);

        assertEquals(99L, warehouse.getManagerId());
        verify(warehouseRepo).save(warehouse);
    }

    @Test
    void getStock_success() {
        when(stockRepo.findByWarehouseId(1L)).thenReturn(List.of(stock));

        assertEquals(2L, service.getStock(1L, 2L).getProductId());
    }

    @Test
    void getStock_notFound() {
        when(stockRepo.findByWarehouseId(1L)).thenReturn(List.of());

        assertThrows(RuntimeException.class, () -> service.getStock(1L, 2L));
    }

    @Test
    void updateStock_success() {
        when(productClient.getProductById(2L)).thenReturn(product);
        when(warehouseRepo.findById(1L)).thenReturn(Optional.of(warehouse));
        when(stockRepo.findByWarehouseId(1L)).thenReturn(List.of(stock));
        when(stockRepo.save(stock)).thenReturn(stock);
        when(stockRepo.findByProductId(2L)).thenReturn(List.of(stock));

        StockLevel saved = service.updateStock(1L, 2L, 15, "Manual GRN");

        assertEquals(15, saved.getQuantity());
        assertEquals(15, warehouse.getUsedCapacity());
        verify(productClient).updateTotalStock(2L, 15);
        verify(stockEventPublisher).publishStockMovement(eq(2L), eq(1L), eq(5), eq("IN"), eq("Manual GRN"));
    }

    @Test
    void updateStock_invalidProduct() {
        when(productClient.getProductById(2L)).thenThrow(new RuntimeException("missing"));

        assertThrows(RuntimeException.class, () -> service.updateStock(1L, 2L, 15, "Manual GRN"));
    }

    @Test
    void updateStock_capacityExceeded() {
        warehouse.setCapacity(12);
        when(productClient.getProductById(2L)).thenReturn(product);
        when(warehouseRepo.findById(1L)).thenReturn(Optional.of(warehouse));
        when(stockRepo.findByWarehouseId(1L)).thenReturn(List.of(stock));

        assertThrows(RuntimeException.class, () -> service.updateStock(1L, 2L, 20, "Manual GRN"));
    }

    @Test
    void updateStock_publishesLowStockAlertWhenBelowThreshold() {
        ReflectionTestUtils.setField(service, "lowStockThreshold", 20);
        when(productClient.getProductById(2L)).thenReturn(product);
        when(warehouseRepo.findById(1L)).thenReturn(Optional.of(warehouse));
        when(stockRepo.findByWarehouseId(1L)).thenReturn(List.of(stock));
        when(stockRepo.save(stock)).thenReturn(stock);
        when(stockRepo.findByProductId(2L)).thenReturn(List.of(stock));

        service.updateStock(1L, 2L, 15, "Cycle count");

        verify(stockEventPublisher).publishStockAlert(
                2L,
                1L,
                15,
                com.stockpro.warehouse.config.RabbitMQConfig.STOCK_LOW_ROUTING_KEY
        );
    }

    @Test
    void updateStock_publishesHighStockAlertWhenAboveMaxLevel() {
        stock.setMaxStockLevel(12);
        when(productClient.getProductById(2L)).thenReturn(product);
        when(warehouseRepo.findById(1L)).thenReturn(Optional.of(warehouse));
        when(stockRepo.findByWarehouseId(1L)).thenReturn(List.of(stock));
        when(stockRepo.save(stock)).thenReturn(stock);
        when(stockRepo.findByProductId(2L)).thenReturn(List.of(stock));

        service.updateStock(1L, 2L, 15, "Cycle count");

        verify(stockEventPublisher).publishStockAlert(
                2L,
                1L,
                15,
                com.stockpro.warehouse.config.RabbitMQConfig.STOCK_HIGH_ROUTING_KEY
        );
    }

    @Test
    void adjustStock_increasesQuantity() {
        StockAdjustmentRequest request = adjustment("IN", 10);
        when(stockRepo.findByWarehouseIdAndProductId(1L, 2L)).thenReturn(Optional.of(stock));
        when(productClient.getProductById(2L)).thenReturn(product);
        when(warehouseRepo.findById(1L)).thenReturn(Optional.of(warehouse));
        when(stockRepo.save(stock)).thenReturn(stock);
        when(stockRepo.findByProductId(2L)).thenReturn(List.of(stock));

        StockLevel saved = service.adjustStock(request);

        assertEquals(20, saved.getQuantity());
        assertEquals(20, warehouse.getUsedCapacity());
    }

    @Test
    void adjustStock_absoluteAdjustmentSetsQuantity() {
        StockAdjustmentRequest request = adjustment("ADJUSTMENT", 7);
        when(stockRepo.findByWarehouseIdAndProductId(1L, 2L)).thenReturn(Optional.of(stock));
        when(productClient.getProductById(2L)).thenReturn(product);
        when(warehouseRepo.findById(1L)).thenReturn(Optional.of(warehouse));
        when(stockRepo.save(stock)).thenReturn(stock);
        when(stockRepo.findByProductId(2L)).thenReturn(List.of(stock));

        assertEquals(7, service.adjustStock(request).getQuantity());
    }

    @Test
    void adjustStock_rejectsDeductionWhenStockMissing() {
        when(stockRepo.findByWarehouseIdAndProductId(1L, 2L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.adjustStock(adjustment("OUT", 1)));
    }

    @Test
    void adjustStock_rejectsInsufficientStock() {
        when(stockRepo.findByWarehouseIdAndProductId(1L, 2L)).thenReturn(Optional.of(stock));

        assertThrows(RuntimeException.class, () -> service.adjustStock(adjustment("OUT", 99)));
    }

    @Test
    void adjustStock_rejectsInvalidType() {
        StockAdjustmentRequest request = adjustment("BAD", 1);

        assertThrows(RuntimeException.class, () -> service.adjustStock(request));
    }

    @Test
    void addStock_success() {
        when(stockRepo.findByWarehouseId(1L)).thenReturn(List.of(stock));
        when(productClient.getProductById(2L)).thenReturn(product);
        when(warehouseRepo.findById(1L)).thenReturn(Optional.of(warehouse));
        when(stockRepo.save(stock)).thenReturn(stock);
        when(stockRepo.findByProductId(2L)).thenReturn(List.of(stock));

        assertEquals(15, service.addStock(1L, 2L, 5, "PO Receipt").getQuantity());
    }

    @Test
    void deleteStockEntry_success() {
        when(stockRepo.findByWarehouseId(1L)).thenReturn(List.of(stock));
        when(warehouseRepo.findById(1L)).thenReturn(Optional.of(warehouse));

        service.deleteStockEntry(1L, 2L);

        assertEquals(0, warehouse.getUsedCapacity());
        verify(stockRepo).delete(stock);
    }

    @Test
    void deleteStockEntry_notFound() {
        when(stockRepo.findByWarehouseId(1L)).thenReturn(List.of());

        assertThrows(RuntimeException.class, () -> service.deleteStockEntry(1L, 2L));
    }

    @Test
    void updateStockThreshold_success() {
        when(stockRepo.findByWarehouseIdAndProductId(1L, 2L)).thenReturn(Optional.of(stock));

        service.updateStockThreshold(1L, 2L, 20);

        assertEquals(20, stock.getMinThreshold());
        verify(stockRepo).save(stock);
    }

    @Test
    void updateStockThreshold_notFound() {
        when(stockRepo.findByWarehouseIdAndProductId(1L, 2L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.updateStockThreshold(1L, 2L, 20));
    }

    @Test
    void reserveStock_success() {
        when(stockRepo.findByWarehouseId(1L)).thenReturn(List.of(stock));

        service.reserveStock(1L, 2L, 3);

        assertEquals(5, stock.getReservedQuantity());
        verify(stockRepo).save(stock);
    }

    @Test
    void reserveStock_insufficientAvailable() {
        when(stockRepo.findByWarehouseId(1L)).thenReturn(List.of(stock));

        assertThrows(RuntimeException.class, () -> service.reserveStock(1L, 2L, 99));
    }

    @Test
    void releaseReservation_success() {
        when(stockRepo.findByWarehouseId(1L)).thenReturn(List.of(stock));

        service.releaseReservation(1L, 2L, 1);

        assertEquals(1, stock.getReservedQuantity());
        verify(stockRepo).save(stock);
    }

    @Test
    void getLowStockReport_success() {
        stock.setQuantity(4);
        stock.setMinThreshold(5);
        StockLevel healthy = new StockLevel();
        healthy.setQuantity(50);
        healthy.setMinThreshold(5);
        when(stockRepo.findByWarehouseId(1L)).thenReturn(List.of(stock, healthy));

        assertEquals(1, service.getLowStockReport(1L).size());
    }

    @Test
    void getWarehouseInventory_successUsesProductDetails() {
        when(stockRepo.findByWarehouseId(1L)).thenReturn(List.of(stock));
        when(productClient.getProductById(2L)).thenReturn(product);

        List<InventoryItemDTO> result = service.getWarehouseInventory(1L);

        assertEquals(1, result.size());
        assertEquals("Phone", result.get(0).getProductName());
        assertEquals("PHN-001", result.get(0).getSku());
    }

    @Test
    void getWarehouseInventory_productServiceDownUsesFallback() {
        when(stockRepo.findByWarehouseId(1L)).thenReturn(List.of(stock));
        when(productClient.getProductById(2L)).thenThrow(new RuntimeException("down"));

        assertEquals("Product #2", service.getWarehouseInventory(1L).get(0).getProductName());
    }

    @Test
    void applyTransfer_success() {
        StockTransferRequest request = transferRequest();
        StockLevel target = new StockLevel();
        target.setWarehouseId(2L);
        target.setProductId(2L);
        target.setQuantity(1);
        target.setReservedQuantity(0);
        Warehouse targetWarehouse = new Warehouse();
        targetWarehouse.setWarehouseId(2L);
        targetWarehouse.setCapacity(50);
        targetWarehouse.setUsedCapacity(1);
        when(stockRepo.findByWarehouseId(1L)).thenReturn(List.of(stock));
        when(stockRepo.findByWarehouseId(2L)).thenReturn(List.of(target));
        when(warehouseRepo.findById(1L)).thenReturn(Optional.of(warehouse));
        when(warehouseRepo.findById(2L)).thenReturn(Optional.of(targetWarehouse));

        StockTransferRequest saved = service.applyTransfer(request);

        assertSame(request, saved);
        assertEquals(6, stock.getQuantity());
        assertEquals(5, target.getQuantity());
        assertEquals(6, warehouse.getUsedCapacity());
        assertEquals(5, targetWarehouse.getUsedCapacity());
    }

    @Test
    void applyTransfer_insufficientStock() {
        StockTransferRequest request = transferRequest();
        request.setQty(99);
        when(stockRepo.findByWarehouseId(1L)).thenReturn(List.of(stock));

        assertThrows(RuntimeException.class, () -> service.applyTransfer(request));
    }

    @Test
    void applyTransfer_targetCapacityExceeded() {
        StockTransferRequest request = transferRequest();
        Warehouse targetWarehouse = new Warehouse();
        targetWarehouse.setWarehouseId(2L);
        targetWarehouse.setCapacity(2);
        targetWarehouse.setUsedCapacity(1);
        when(stockRepo.findByWarehouseId(1L)).thenReturn(List.of(stock));
        when(stockRepo.findByWarehouseId(2L)).thenReturn(List.of());
        when(warehouseRepo.findById(1L)).thenReturn(Optional.of(warehouse));
        when(warehouseRepo.findById(2L)).thenReturn(Optional.of(targetWarehouse));

        assertThrows(RuntimeException.class, () -> service.applyTransfer(request));
    }

    @Test
    void issueStock_success() {
        StockIssueRequest request = issueRequest();
        when(stockRepo.findByWarehouseId(1L)).thenReturn(List.of(stock));
        when(warehouseRepo.findById(1L)).thenReturn(Optional.of(warehouse));
        when(stockRepo.save(stock)).thenReturn(stock);
        when(stockRepo.findByProductId(2L)).thenReturn(List.of(stock));

        StockLevel saved = service.issueStock(request);

        assertEquals(7, saved.getQuantity());
        assertEquals(7, warehouse.getUsedCapacity());
        verify(stockEventPublisher).publishStockMovement(eq(2L), eq(1L), eq(3), eq("ISSUE"), contains("SALES"));
    }

    @Test
    void issueStock_invalidIssueType() {
        StockIssueRequest request = issueRequest();
        request.setIssueType("BAD");

        assertThrows(RuntimeException.class, () -> service.issueStock(request));
    }

    @Test
    void issueStock_insufficientAvailable() {
        StockIssueRequest request = issueRequest();
        request.setQuantity(9);
        when(stockRepo.findByWarehouseId(1L)).thenReturn(List.of(stock));

        assertThrows(RuntimeException.class, () -> service.issueStock(request));
    }

    @Test
    void writeOffStock_success() {
        StockWriteOffRequest request = writeOffRequest();
        when(stockRepo.findByWarehouseId(1L)).thenReturn(List.of(stock));
        when(stockRepo.save(stock)).thenReturn(stock);
        when(warehouseRepo.findById(1L)).thenReturn(Optional.of(warehouse));
        when(stockRepo.findByProductId(2L)).thenReturn(List.of(stock));

        StockLevel saved = service.writeOffStock(request);

        assertEquals(6, saved.getQuantity());
        assertEquals(6, warehouse.getUsedCapacity());
        verify(stockEventPublisher).publishStockMovement(eq(2L), eq(1L), eq(4), eq("WRITE_OFF"), contains("DAMAGED"));
    }

    @Test
    void writeOffStock_moreThanAvailable() {
        StockWriteOffRequest request = writeOffRequest();
        request.setQuantity(99);
        when(stockRepo.findByWarehouseId(1L)).thenReturn(List.of(stock));

        assertThrows(RuntimeException.class, () -> service.writeOffStock(request));
    }

    @Test
    void returnStock_success() {
        StockReturnRequest request = returnRequest();
        when(stockRepo.findByWarehouseId(1L)).thenReturn(List.of(stock));
        when(stockRepo.save(stock)).thenReturn(stock);
        when(warehouseRepo.findById(1L)).thenReturn(Optional.of(warehouse));
        when(stockRepo.findByProductId(2L)).thenReturn(List.of(stock));

        StockLevel saved = service.returnStock(request);

        assertEquals(12, saved.getQuantity());
        assertEquals(12, warehouse.getUsedCapacity());
        verify(stockEventPublisher).publishStockMovement(eq(2L), eq(1L), eq(2), eq("RETURN"), contains("CUSTOMER_RETURN"));
    }

    @Test
    void returnStock_invalidReturnType() {
        StockReturnRequest request = returnRequest();
        request.setReturnType("BAD");

        assertThrows(RuntimeException.class, () -> service.returnStock(request));
    }

    private StockAdjustmentRequest adjustment(String type, int quantity) {
        StockAdjustmentRequest request = new StockAdjustmentRequest();
        request.setWarehouseId(1L);
        request.setProductId(2L);
        request.setAdjustmentType(type);
        request.setQuantity(quantity);
        request.setReason("Cycle count");
        return request;
    }

    private StockTransferRequest transferRequest() {
        StockTransferRequest request = new StockTransferRequest();
        request.setProductId(2L);
        request.setFromWarehouse(1L);
        request.setToWarehouse(2L);
        request.setQty(4);
        request.setReason("Rebalance");
        return request;
    }

    private StockIssueRequest issueRequest() {
        StockIssueRequest request = new StockIssueRequest();
        request.setWarehouseId(1L);
        request.setProductId(2L);
        request.setQuantity(3);
        request.setIssueType("SALES");
        request.setNotes("SO-1");
        return request;
    }

    private StockWriteOffRequest writeOffRequest() {
        StockWriteOffRequest request = new StockWriteOffRequest();
        request.setWarehouseId(1L);
        request.setProductId(2L);
        request.setQuantity(4);
        request.setWriteOffReason("DAMAGED");
        request.setNotes("Broken");
        return request;
    }

    private StockReturnRequest returnRequest() {
        StockReturnRequest request = new StockReturnRequest();
        request.setWarehouseId(1L);
        request.setProductId(2L);
        request.setQuantity(2);
        request.setReturnType("CUSTOMER_RETURN");
        request.setReferenceNumber("RET-1");
        request.setNotes("Box sealed");
        return request;
    }
}
