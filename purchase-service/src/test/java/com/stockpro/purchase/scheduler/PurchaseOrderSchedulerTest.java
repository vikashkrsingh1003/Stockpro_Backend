package com.stockpro.purchase.scheduler;

import com.stockpro.purchase.entity.POStatus;
import com.stockpro.purchase.entity.PurchaseOrder;
import com.stockpro.purchase.repository.PurchaseOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderSchedulerTest {

    @Mock
    private PurchaseOrderRepository repository;

    @Test
    void checkOverduePurchaseOrdersDoesNothingWhenNoneFound() {
        PurchaseOrderScheduler scheduler = new PurchaseOrderScheduler(repository);
        when(repository.findByStatusInAndExpectedDeliveryDateBefore(any(), any())).thenReturn(List.of());

        scheduler.checkOverduePurchaseOrders();

        verify(repository, never()).save(any());
    }

    @Test
    void checkOverduePurchaseOrdersMarksApprovedAndPartiallyReceivedAsOverdue() {
        PurchaseOrderScheduler scheduler = new PurchaseOrderScheduler(repository);
        PurchaseOrder po = PurchaseOrder.builder()
                .referenceNumber("PO-20")
                .status(POStatus.APPROVED)
                .expectedDeliveryDate(LocalDateTime.now().minusDays(1))
                .build();
        when(repository.findByStatusInAndExpectedDeliveryDateBefore(any(), any())).thenReturn(List.of(po));

        scheduler.checkOverduePurchaseOrders();

        ArgumentCaptor<List<POStatus>> statuses = ArgumentCaptor.forClass(List.class);
        verify(repository).findByStatusInAndExpectedDeliveryDateBefore(statuses.capture(), any());
        assertTrue(statuses.getValue().contains(POStatus.APPROVED));
        assertTrue(statuses.getValue().contains(POStatus.PARTIALLY_RECEIVED));
        assertEquals(POStatus.OVERDUE, po.getStatus());
        verify(repository).save(po);
    }
}
