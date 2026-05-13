package com.stockpro.alert.listerner;

import com.stockpro.alert.dto.PurchaseOrderEvent;
import com.stockpro.alert.dto.StockAlertEvent;
import com.stockpro.alert.entity.AlertSeverity;
import com.stockpro.alert.entity.AlertType;
import com.stockpro.alert.service.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertEventListenerTest {

    @Mock
    private AlertService alertService;

    private AlertEventListener listener;

    @BeforeEach
    void setup() {
        listener = new AlertEventListener(alertService);
    }

    @Test
    void lowStock_createsCriticalLowStockAlert() {
        StockAlertEvent event = stockEvent(2L, 1L, 8);

        listener.lowStock(event);

        verify(alertService).createAlert(
                eq(AlertType.LOW_STOCK),
                eq(AlertSeverity.CRITICAL),
                eq("Low Stock Alert"),
                contains("Current stock: 8"),
                eq(2L),
                eq(1L),
                isNull()
        );
    }

    @Test
    void overStock_createsWarningOverstockAlert() {
        StockAlertEvent event = stockEvent(2L, 1L, 150);

        listener.overStock(event);

        verify(alertService).createAlert(
                eq(AlertType.OVERSTOCK),
                eq(AlertSeverity.WARNING),
                eq("Overstock Alert"),
                contains("150 units"),
                eq(2L),
                eq(1L),
                isNull()
        );
    }

    @Test
    void poPending_createsInfoPendingAlert() {
        PurchaseOrderEvent event = new PurchaseOrderEvent();
        event.setPoId(5L);
        event.setWarehouseId(1L);
        event.setReferenceNumber("PO-5");

        listener.poPending(event);

        verify(alertService).createAlert(
                eq(AlertType.PO_PENDING),
                eq(AlertSeverity.INFO),
                eq("PO Pending"),
                eq("PO PO-5 pending"),
                isNull(),
                eq(1L),
                eq(5L)
        );
    }

    @Test
    void lowStock_alertServiceFailureIsSwallowed() {
        StockAlertEvent event = stockEvent(2L, 1L, 8);
        when(alertService.createAlert(any(), any(), anyString(), anyString(), anyLong(), anyLong(), isNull()))
                .thenThrow(new RuntimeException("database down"));

        listener.lowStock(event);

        verify(alertService).createAlert(any(), any(), anyString(), anyString(), anyLong(), anyLong(), isNull());
    }

    private StockAlertEvent stockEvent(Long productId, Long warehouseId, Integer qty) {
        StockAlertEvent event = new StockAlertEvent();
        event.setProductId(productId);
        event.setWarehouseId(warehouseId);
        event.setCurrentQty(qty);
        return event;
    }
}
