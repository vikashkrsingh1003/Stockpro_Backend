package com.stockpro.alert.scheduler;

import com.stockpro.alert.client.PurchaseClient;
import com.stockpro.alert.dto.PurchaseOrderDTO;
import com.stockpro.alert.entity.AlertSeverity;
import com.stockpro.alert.entity.AlertType;
import com.stockpro.alert.service.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OverduePOSchedulerTest {

    @Mock
    private AlertService alertService;

    @Mock
    private PurchaseClient purchaseClient;

    private OverduePOScheduler scheduler;

    @BeforeEach
    void setup() {
        scheduler = new OverduePOScheduler(alertService, purchaseClient);
    }

    @Test
    void checkOverduePOs_createsCriticalAlertForEachOverduePO() {
        PurchaseOrderDTO po = overduePo(5L, "PO-5", 2L);
        when(purchaseClient.getOverduePOs()).thenReturn(List.of(po));

        scheduler.checkOverduePOs();

        verify(alertService).createAlert(
                eq(AlertType.OVERDUE_PO),
                eq(AlertSeverity.CRITICAL),
                eq("Overdue Purchase Order"),
                contains("PO #PO-5"),
                isNull(),
                eq(2L),
                eq(5L)
        );
    }

    @Test
    void checkOverduePOs_emptyListDoesNotCreateAlerts() {
        when(purchaseClient.getOverduePOs()).thenReturn(List.of());

        scheduler.checkOverduePOs();

        verifyNoInteractions(alertService);
    }

    @Test
    void checkOverduePOs_nullListDoesNotCreateAlerts() {
        when(purchaseClient.getOverduePOs()).thenReturn(null);

        scheduler.checkOverduePOs();

        verifyNoInteractions(alertService);
    }

    @Test
    void checkOverduePOs_clientFailureIsSwallowed() {
        when(purchaseClient.getOverduePOs()).thenThrow(new RuntimeException("purchase-service down"));

        scheduler.checkOverduePOs();

        verifyNoInteractions(alertService);
    }

    private PurchaseOrderDTO overduePo(Long id, String reference, Long warehouseId) {
        PurchaseOrderDTO po = new PurchaseOrderDTO();
        po.setId(id);
        po.setReferenceNumber(reference);
        po.setWarehouseId(warehouseId);
        po.setExpectedDeliveryDate(LocalDateTime.now().minusDays(1));
        return po;
    }
}
