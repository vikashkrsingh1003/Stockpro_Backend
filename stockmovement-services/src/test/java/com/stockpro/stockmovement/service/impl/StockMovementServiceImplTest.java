package com.stockpro.stockmovement.service.impl;

import com.stockpro.stockmovement.client.WarehouseClient;
import com.stockpro.stockmovement.dto.StockTransferRequest;
import com.stockpro.stockmovement.entity.MovementType;
import com.stockpro.stockmovement.entity.StockMovement;
import com.stockpro.stockmovement.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockMovementServiceImplTest {

    @Mock
    private StockMovementRepository movementRepo;

    @Mock
    private WarehouseClient warehouseClient;

    @InjectMocks
    private StockMovementServiceImpl service;

    private StockMovement movement;

    @BeforeEach
    void setup() {
        movement = StockMovement.builder()
                .movementId(1L)
                .warehouseId(1L)
                .productId(2L)
                .quantity(5)
                .type(MovementType.IN)
                .reason("Opening stock")
                .performedBy("staff@mail.com")
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Test
    void record_success() {
        when(movementRepo.save(movement)).thenReturn(movement);

        assertEquals(1L, service.record(movement).getMovementId());
    }

    @Test
    void getByWarehouse_success() {
        when(movementRepo.findByWarehouseIdOrderByTimestampDesc(1L)).thenReturn(List.of(movement));

        assertEquals(1, service.getByWarehouse(1L).size());
    }

    @Test
    void getByProduct_success() {
        when(movementRepo.findByProductIdOrderByTimestampDesc(2L)).thenReturn(List.of(movement));

        assertEquals(1, service.getByProduct(2L).size());
    }

    @Test
    void getByWarehouseAndProduct_success() {
        when(movementRepo.findByWarehouseIdAndProductIdOrderByTimestampDesc(1L, 2L)).thenReturn(List.of(movement));

        assertEquals(1, service.getByWarehouseAndProduct(1L, 2L).size());
    }

    @Test
    void getFiltered_success() {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();
        when(movementRepo.findFiltered(1L, 2L, MovementType.IN, from, to)).thenReturn(List.of(movement));

        assertEquals(1, service.getFiltered(1L, 2L, MovementType.IN, from, to).size());
    }

    @Test
    void transfer_success_recordsTwoRows() {
        StockTransferRequest request = transferRequest();

        service.transfer(request, "staff@mail.com");

        verify(warehouseClient).applyTransfer(request);
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(movementRepo, times(2)).save(captor.capture());
        assertEquals(-4, captor.getAllValues().get(0).getQuantity());
        assertEquals(4, captor.getAllValues().get(1).getQuantity());
        assertEquals(MovementType.TRANSFER, captor.getAllValues().get(0).getType());
    }

    @Test
    void transfer_usesDefaultReasonWhenBlank() {
        StockTransferRequest request = transferRequest();
        request.setReason(" ");

        service.transfer(request, "staff@mail.com");

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(movementRepo, times(2)).save(captor.capture());
        assertEquals("Inter-warehouse transfer", captor.getAllValues().get(0).getReason());
    }

    private StockTransferRequest transferRequest() {
        StockTransferRequest request = new StockTransferRequest();
        request.setProductId(7L);
        request.setFromWarehouse(1L);
        request.setToWarehouse(2L);
        request.setQty(4);
        request.setReason("Rebalance");
        return request;
    }
}
