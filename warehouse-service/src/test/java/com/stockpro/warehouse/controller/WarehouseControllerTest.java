package com.stockpro.warehouse.controller;

import com.stockpro.warehouse.dto.InventoryItemDTO;
import com.stockpro.warehouse.dto.StockAdjustmentRequest;
import com.stockpro.warehouse.dto.StockIssueRequest;
import com.stockpro.warehouse.dto.StockReturnRequest;
import com.stockpro.warehouse.dto.StockTransferRequest;
import com.stockpro.warehouse.dto.StockWriteOffRequest;
import com.stockpro.warehouse.entity.StockLevel;
import com.stockpro.warehouse.entity.Warehouse;
import com.stockpro.warehouse.service.WarehouseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarehouseControllerTest {

    @Mock
    private WarehouseService service;

    private WarehouseController controller;
    private Warehouse warehouse;
    private StockLevel stockLevel;

    @BeforeEach
    void setup() {
        controller = new WarehouseController(service);
        ReflectionTestUtils.setField(controller, "internalServiceToken", "internal-token");

        warehouse = new Warehouse();
        warehouse.setWarehouseId(1L);
        warehouse.setName("Main");

        stockLevel = new StockLevel();
        stockLevel.setWarehouseId(1L);
        stockLevel.setProductId(2L);
        stockLevel.setQuantity(10);
    }

    @Test
    void warehouseCrudEndpointsDelegateToService() {
        when(service.createWarehouse(warehouse)).thenReturn(warehouse);
        when(service.getAllWarehouses()).thenReturn(List.of(warehouse));
        when(service.getWarehouseById(1L)).thenReturn(warehouse);
        when(service.updateWarehouse(1L, warehouse)).thenReturn(warehouse);

        assertSame(warehouse, controller.createWarehouse(warehouse));
        assertEquals(List.of(warehouse), controller.getAllWarehouses());
        assertSame(warehouse, controller.getById(1L));
        assertSame(warehouse, controller.updateWarehouse(1L, warehouse));
        assertEquals("Warehouse Activated", controller.setStatus(1L, true));
        assertEquals("Warehouse Deactivated", controller.setStatus(1L, false));
        assertEquals("Manager assigned", controller.assignManager(1L, 9L));

        verify(service).setWarehouseActive(1L, true);
        verify(service).setWarehouseActive(1L, false);
        verify(service).assignManager(1L, 9L);
    }

    @Test
    void stockEndpointsDelegateToService() {
        StockAdjustmentRequest adjustment = new StockAdjustmentRequest();
        StockIssueRequest issue = new StockIssueRequest();
        StockWriteOffRequest writeOff = new StockWriteOffRequest();
        StockReturnRequest stockReturn = new StockReturnRequest();
        InventoryItemDTO item = InventoryItemDTO.builder().productId(2L).quantity(10).build();

        when(service.getStock(1L, 2L)).thenReturn(stockLevel);
        when(service.updateStock(1L, 2L, 10, "count")).thenReturn(stockLevel);
        when(service.adjustStock(adjustment)).thenReturn(stockLevel);
        when(service.addStock(1L, 2L, 5, "received")).thenReturn(stockLevel);
        when(service.getLowStockReport(1L)).thenReturn(List.of(stockLevel));
        when(service.getWarehouseInventory(1L)).thenReturn(List.of(item));
        when(service.issueStock(issue)).thenReturn(stockLevel);
        when(service.writeOffStock(writeOff)).thenReturn(stockLevel);
        when(service.returnStock(stockReturn)).thenReturn(stockLevel);

        assertSame(stockLevel, controller.getStock(1L, 2L));
        assertSame(stockLevel, controller.updateStock(1L, 2L, 10, "count"));
        assertSame(stockLevel, controller.adjustStock(adjustment));
        assertSame(stockLevel, controller.addStock(1L, 2L, 5, "received"));
        assertEquals("Stock entry deleted", controller.deleteStockEntry(1L, 2L));
        assertEquals("Threshold updated", controller.updateThreshold(1L, 2L, 4));
        assertEquals("Stock Reserved", controller.reserveStock(1L, 2L, 3));
        assertEquals("Stock Released", controller.releaseStock(1L, 2L, 3));
        assertEquals(List.of(stockLevel), controller.getLowStock(1L));
        assertEquals(List.of(item), controller.getInventory(1L));
        assertSame(stockLevel, controller.issueStock(issue));
        assertSame(stockLevel, controller.writeOffStock(writeOff));
        assertSame(stockLevel, controller.returnStock(stockReturn));

        verify(service).deleteStockEntry(1L, 2L);
        verify(service).updateStockThreshold(1L, 2L, 4);
        verify(service).reserveStock(1L, 2L, 3);
        verify(service).releaseReservation(1L, 2L, 3);
    }

    @Test
    void internalTransferRequiresValidToken() {
        StockTransferRequest request = new StockTransferRequest();
        when(service.applyTransfer(request)).thenReturn(request);

        assertSame(request, controller.applyTransfer("internal-token", request));
        assertThrows(ResponseStatusException.class, () -> controller.applyTransfer("bad-token", request));
    }
}
