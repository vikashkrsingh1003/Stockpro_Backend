package com.stockpro.analytics.listener;

import com.stockpro.analytics.dto.PurchaseOrderEvent;
import com.stockpro.analytics.dto.StockMovementEvent;
import com.stockpro.analytics.entity.InventorySnapshot;
import com.stockpro.analytics.entity.ProductPerformance;
import com.stockpro.analytics.entity.SupplierSpend;
import com.stockpro.analytics.repository.ProductPerformanceRepository;
import com.stockpro.analytics.repository.SnapshotRepository;
import com.stockpro.analytics.repository.SupplierSpendRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsListenerTest {

    @Mock
    private SnapshotRepository snapshotRepository;

    @Mock
    private ProductPerformanceRepository performanceRepository;

    @Mock
    private SupplierSpendRepository supplierSpendRepository;

    @Test
    void handleStockMovementCreatesSnapshotAndNewPerformance() {
        AnalyticsListener listener = new AnalyticsListener(snapshotRepository, performanceRepository, supplierSpendRepository);
        StockMovementEvent event = new StockMovementEvent(2L, 1L, 5, "IN", "received", LocalDateTime.now());
        when(performanceRepository.findById(2L)).thenReturn(Optional.empty());
        when(snapshotRepository.findByProductId(2L)).thenReturn(List.of(new InventorySnapshot()));

        listener.handleStockMovement(event);

        ArgumentCaptor<InventorySnapshot> snapshotCaptor = ArgumentCaptor.forClass(InventorySnapshot.class);
        verify(snapshotRepository).save(snapshotCaptor.capture());
        assertEquals(2L, snapshotCaptor.getValue().getProductId());
        assertEquals(5, snapshotCaptor.getValue().getQuantity());

        ArgumentCaptor<ProductPerformance> performanceCaptor = ArgumentCaptor.forClass(ProductPerformance.class);
        verify(performanceRepository).save(performanceCaptor.capture());
        assertEquals("VERY_SLOW", performanceCaptor.getValue().getMovementCategory());
    }

    @Test
    void handleStockMovementUpdatesExistingPerformanceCategories() {
        AnalyticsListener listener = new AnalyticsListener(snapshotRepository, performanceRepository, supplierSpendRepository);
        ProductPerformance existing = new ProductPerformance();
        existing.setProductId(2L);
        existing.setTurnoverRate(0.0);
        existing.setMovementCategory("NEW");
        when(performanceRepository.findById(2L)).thenReturn(Optional.of(existing));

        InventorySnapshot first = new InventorySnapshot();
        first.setSnapshotDate(LocalDateTime.now().minusDays(10));
        List<InventorySnapshot> movements = java.util.stream.IntStream.range(0, 50)
                .mapToObj(i -> first)
                .toList();
        when(snapshotRepository.findByProductId(2L)).thenReturn(movements);

        listener.handleStockMovement(new StockMovementEvent(2L, 1L, 9, "OUT", "issue", null));

        assertEquals("TOP_MOVING", existing.getMovementCategory());
        assertEquals(5.0, existing.getTurnoverRate());
        verify(performanceRepository).save(existing);
    }

    @Test
    void handleStockMovementSwallowsRepositoryFailure() {
        AnalyticsListener listener = new AnalyticsListener(snapshotRepository, performanceRepository, supplierSpendRepository);
        doThrow(new RuntimeException("db down")).when(snapshotRepository).save(any(InventorySnapshot.class));

        assertDoesNotThrow(() -> listener.handleStockMovement(
                new StockMovementEvent(2L, 1L, 5, "IN", "received", LocalDateTime.now())
        ));
    }

    @Test
    void purchaseOrderLogOnlyHandlersDoNotThrow() {
        AnalyticsListener listener = new AnalyticsListener(snapshotRepository, performanceRepository, supplierSpendRepository);
        PurchaseOrderEvent event = new PurchaseOrderEvent(10L, "PO-10", 3L, 1L, 500.0, "APPROVED", LocalDateTime.now());

        assertDoesNotThrow(() -> listener.handlePoSubmitted(event));
        assertDoesNotThrow(() -> listener.handlePoApproved(event));
    }

    @Test
    void handlePoReceivedCreatesAndUpdatesSupplierSpend() {
        AnalyticsListener listener = new AnalyticsListener(snapshotRepository, performanceRepository, supplierSpendRepository);
        PurchaseOrderEvent event = new PurchaseOrderEvent(10L, "PO-10", 3L, 1L, 500.0, "RECEIVED", LocalDateTime.now());
        when(supplierSpendRepository.findById(3L)).thenReturn(Optional.empty());

        listener.handlePoReceived(event);

        ArgumentCaptor<SupplierSpend> spendCaptor = ArgumentCaptor.forClass(SupplierSpend.class);
        verify(supplierSpendRepository).save(spendCaptor.capture());
        assertEquals(3L, spendCaptor.getValue().getSupplierId());
        assertEquals(500.0, spendCaptor.getValue().getTotalSpend());
        assertEquals(1, spendCaptor.getValue().getTotalOrdersReceived());
    }

    @Test
    void handlePoReceivedSkipsInvalidEventAndSwallowsFailure() {
        AnalyticsListener listener = new AnalyticsListener(snapshotRepository, performanceRepository, supplierSpendRepository);

        listener.handlePoReceived(new PurchaseOrderEvent(10L, "PO-10", null, 1L, 500.0, "RECEIVED", LocalDateTime.now()));
        verifyNoInteractions(supplierSpendRepository);

        reset(supplierSpendRepository);
        PurchaseOrderEvent event = new PurchaseOrderEvent(10L, "PO-11", 3L, 1L, 500.0, "RECEIVED", LocalDateTime.now());
        when(supplierSpendRepository.findById(3L)).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> listener.handlePoReceived(event));
    }
}
