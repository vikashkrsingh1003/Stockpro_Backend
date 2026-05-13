package com.stockpro.analytics.controller;

import com.stockpro.analytics.dto.GlobalMetricsDTO;
import com.stockpro.analytics.dto.ProductPerformanceDTO;
import com.stockpro.analytics.dto.SupplierSpendDTO;
import com.stockpro.analytics.dto.WarehouseUtilizationDTO;
import com.stockpro.analytics.service.AnalyticsService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

    @Mock
    private AnalyticsService analyticsService;

    @Mock
    private HttpServletResponse servletResponse;

    private AnalyticsController controller;

    @BeforeEach
    void setup() {
        controller = new AnalyticsController(analyticsService);
    }

    @Test
    void readEndpointsDelegateToService() {
        GlobalMetricsDTO dashboard = new GlobalMetricsDTO();
        ProductPerformanceDTO performance = new ProductPerformanceDTO(1L, "SKU", 2.0, "TOP_MOVING", null);
        WarehouseUtilizationDTO utilization = new WarehouseUtilizationDTO(1L, "WH", 5, 10, 50.0, "NORMAL");
        SupplierSpendDTO supplierSpend = new SupplierSpendDTO(1L, "Supplier", 100.0, 2, 50.0, "today");
        when(analyticsService.calculateGlobalValuation()).thenReturn(1000.0);
        when(analyticsService.getTopMovingProducts(5)).thenReturn(List.of(performance));
        when(analyticsService.getDeadStock()).thenReturn(List.of(performance));
        when(analyticsService.getGlobalDashboardMetrics()).thenReturn(dashboard);
        when(analyticsService.getWarehouseUtilization()).thenReturn(List.of(utilization));
        when(analyticsService.getSupplierSpend()).thenReturn(List.of(supplierSpend));

        assertEquals(1000.0, controller.getGlobalValuation().getBody());
        assertEquals(1, controller.getTopMoving(5).getBody().size());
        assertEquals(1, controller.getDeadStock().getBody().size());
        assertSame(dashboard, controller.getDashboard().getBody());
        assertEquals(1, controller.getWarehouseUtilization().getBody().size());
        assertEquals(1, controller.getSupplierSpend().getBody().size());
    }

    @Test
    void dashboardReturns500BodyWhenServiceFails() {
        when(analyticsService.getGlobalDashboardMetrics()).thenThrow(new RuntimeException("down"));

        var response = controller.getDashboard();

        assertEquals(500, response.getStatusCode().value());
        assertEquals("Analytics Error: down", response.getBody());
    }

    @Test
    void exportEndpointsDelegateToService() throws IOException {
        controller.exportValuationCsv(servletResponse);
        controller.exportDeadStockCsv(servletResponse);
        controller.exportTopMovingCsv(servletResponse);

        verify(analyticsService).exportValuationCsv(servletResponse);
        verify(analyticsService).exportDeadStockCsv(servletResponse);
        verify(analyticsService).exportTopMovingCsv(servletResponse);
    }
}
