package com.stockpro.analytics.service.impl;

import com.stockpro.analytics.client.ProductClient;
import com.stockpro.analytics.client.WarehouseClient;
import com.stockpro.analytics.dto.ProductDTO;
import com.stockpro.analytics.dto.StockLevelDTO;
import com.stockpro.analytics.dto.WarehouseDTO;
import com.stockpro.analytics.entity.ProductPerformance;
import com.stockpro.analytics.entity.SupplierSpend;
import com.stockpro.analytics.repository.ProductPerformanceRepository;
import com.stockpro.analytics.repository.SnapshotRepository;
import com.stockpro.analytics.repository.SupplierSpendRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceImplTest {

    @Mock
    private ProductClient productClient;

    @Mock
    private WarehouseClient warehouseClient;

    @Mock
    private ProductPerformanceRepository performanceRepository;

    @Mock
    private SnapshotRepository snapshotRepository;

    @Mock
    private SupplierSpendRepository supplierSpendRepository;

    @InjectMocks
    private AnalyticsServiceImpl service;

    private ProductDTO product;
    private WarehouseDTO warehouse;
    private StockLevelDTO stock;
    private ProductPerformance performance;

    @BeforeEach
    void setup() {
        product = new ProductDTO();
        product.setId(10L);
        product.setSku("SKU10");
        product.setCostPrice(100.0);

        warehouse = new WarehouseDTO();
        warehouse.setId(1L);
        warehouse.setName("Main");
        warehouse.setCapacity(100);
        warehouse.setUsedCapacity(75);

        stock = new StockLevelDTO();
        stock.setWarehouseId(1L);
        stock.setProductId(10L);
        stock.setQuantity(3);

        performance = new ProductPerformance();
        performance.setProductId(10L);
        performance.setSku("SKU10");
        performance.setTurnoverRate(8.5);
        performance.setMovementCategory("TOP_MOVING");
        performance.setLastCalculated(LocalDateTime.now());
    }

    @Test
    void calculateGlobalValuation_success() {
        when(productClient.getAllProducts()).thenReturn(List.of(product));
        when(warehouseClient.getAllWarehouses()).thenReturn(List.of(warehouse));
        when(warehouseClient.getWarehouseInventory(1L)).thenReturn(List.of(stock));

        assertEquals(300.0, service.calculateGlobalValuation());
    }

    @Test
    void calculateGlobalValuation_returnsZeroWhenClientFails() {
        when(productClient.getAllProducts()).thenThrow(new RuntimeException("down"));

        assertEquals(0.0, service.calculateGlobalValuation());
    }

    @Test
    void getTopMovingProducts_success() {
        ProductPerformance second = new ProductPerformance();
        second.setProductId(20L);
        second.setSku("SKU20");
        second.setTurnoverRate(4.0);
        second.setMovementCategory("SLOW_MOVING");
        when(performanceRepository.findTop10ByOrderByTurnoverRateDesc()).thenReturn(List.of(performance, second));

        var result = service.getTopMovingProducts(1);

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).getProductId());
    }

    @Test
    void getTopMovingProducts_returnsEmptyWhenRepositoryFails() {
        when(performanceRepository.findTop10ByOrderByTurnoverRateDesc()).thenThrow(new RuntimeException("db"));

        assertTrue(service.getTopMovingProducts(5).isEmpty());
    }

    @Test
    void getDeadStock_success() {
        performance.setMovementCategory("DEAD");
        when(performanceRepository.findByMovementCategory("DEAD")).thenReturn(List.of(performance));

        assertEquals(1, service.getDeadStock().size());
    }

    @Test
    void getDeadStock_returnsEmptyWhenRepositoryFails() {
        when(performanceRepository.findByMovementCategory("DEAD")).thenThrow(new RuntimeException("db"));

        assertTrue(service.getDeadStock().isEmpty());
    }

    @Test
    void getGlobalDashboardMetrics_success() {
        when(productClient.getAllProducts()).thenReturn(List.of(product));
        when(warehouseClient.getAllWarehouses()).thenReturn(List.of(warehouse));
        when(warehouseClient.getWarehouseInventory(1L)).thenReturn(List.of(stock));
        when(performanceRepository.findAll()).thenReturn(List.of(performance));
        when(performanceRepository.findTop10ByOrderByTurnoverRateDesc()).thenReturn(List.of(performance));

        var metrics = service.getGlobalDashboardMetrics();

        assertEquals(300.0, metrics.getTotalInventoryValue());
        assertEquals(1, metrics.getTotalProducts());
        assertEquals(1, metrics.getTotalWarehouses());
        assertEquals(75.0, metrics.getWarehouseUtilization().get("Main"));
        assertEquals(1L, metrics.getTopMovingCount());
    }

    @Test
    void getWarehouseUtilization_success() {
        when(warehouseClient.getAllWarehouses()).thenReturn(List.of(warehouse));

        var result = service.getWarehouseUtilization();

        assertEquals(1, result.size());
        assertEquals(75.0, result.get(0).getUtilizationPercent());
        assertEquals("HIGH", result.get(0).getStatus());
    }

    @Test
    void getSupplierSpend_success() {
        SupplierSpend spend = new SupplierSpend();
        spend.setSupplierId(1L);
        spend.setSupplierName("India Mart");
        spend.setTotalSpend(5000.0);
        spend.setTotalOrdersReceived(2);
        spend.setAvgOrderValue(2500.0);
        spend.setLastPurchaseDate(LocalDateTime.of(2026, 5, 1, 10, 0));
        when(supplierSpendRepository.findTopSuppliersBySpend()).thenReturn(List.of(spend));

        var result = service.getSupplierSpend();

        assertEquals(1, result.size());
        assertEquals("India Mart", result.get(0).getSupplierName());
        assertEquals(5000.0, result.get(0).getTotalSpend());
    }

    @Test
    void exportValuationCsv_success() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(out));
        when(productClient.getAllProducts()).thenReturn(List.of(product));
        when(warehouseClient.getAllWarehouses()).thenReturn(List.of(warehouse));
        when(warehouseClient.getWarehouseInventory(1L)).thenReturn(List.of(stock));

        service.exportValuationCsv(response);

        assertTrue(out.toString().contains("Product ID"));
        assertTrue(out.toString().contains("SKU10"));
        verify(response).setContentType("text/csv");
    }

    @Test
    void exportDeadStockCsv_success() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(out));
        performance.setMovementCategory("DEAD");
        when(performanceRepository.findByMovementCategory("DEAD")).thenReturn(List.of(performance));

        service.exportDeadStockCsv(response);

        verify(response).setHeader("Content-Disposition", "attachment; filename=\"dead_stock_report.csv\"");
        assertTrue(out.toString().contains("Product ID"));
        assertTrue(out.toString().contains("SKU10"));
    }

    @Test
    void exportTopMovingCsv_success() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(out));
        when(performanceRepository.findTop10ByOrderByTurnoverRateDesc()).thenReturn(List.of(performance));

        service.exportTopMovingCsv(response);

        verify(response).setHeader("Content-Disposition", "attachment; filename=\"top_moving_products.csv\"");
        assertTrue(out.toString().contains("Product ID"));
        assertTrue(out.toString().contains("SKU10"));
    }
}
