package com.stockpro.stockmovement.controller;

import com.stockpro.stockmovement.dto.StockTransferRequest;
import com.stockpro.stockmovement.entity.MovementType;
import com.stockpro.stockmovement.entity.StockMovement;
import com.stockpro.stockmovement.service.StockMovementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockMovementControllerTest {

    @Mock
    private StockMovementService movementService;

    @Mock
    private Authentication authentication;

    private StockMovementController controller;
    private StockMovement movement;

    @BeforeEach
    void setup() {
        controller = new StockMovementController(movementService);
        movement = StockMovement.builder()
                .movementId(1L)
                .warehouseId(2L)
                .productId(3L)
                .type(MovementType.IN)
                .quantity(5)
                .build();
    }

    @Test
    void recordDelegatesToService() {
        when(movementService.record(movement)).thenReturn(movement);

        assertSame(movement, controller.record(movement));
    }

    @Test
    void transferUsesAuthenticatedUserAndSystemFallback() {
        StockTransferRequest request = new StockTransferRequest();
        when(authentication.getName()).thenReturn("staff@test.com");

        assertEquals("Product transfer successful", controller.transfer(request, authentication).get("message"));
        assertEquals("Product transfer successful", controller.transfer(request, null).get("message"));

        verify(movementService).transfer(request, "staff@test.com");
        verify(movementService).transfer(request, "SYSTEM");
    }

    @Test
    void filterEndpointsDelegateToService() {
        when(movementService.getFiltered(eq(2L), eq(3L), eq(MovementType.IN), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(movement));
        when(movementService.getByWarehouse(2L)).thenReturn(List.of(movement));
        when(movementService.getByProduct(3L)).thenReturn(List.of(movement));
        when(movementService.getByWarehouseAndProduct(2L, 3L)).thenReturn(List.of(movement));

        assertEquals(1, controller.getFiltered(2L, 3L, MovementType.IN, "2026-05-01", "2026-05-02").size());
        assertEquals(1, controller.getByWarehouse(2L).size());
        assertEquals(1, controller.getByProduct(3L).size());
        assertEquals(1, controller.getByWarehouseAndProduct(2L, 3L).size());
    }

    @Test
    void filterAcceptsFullDateTimeAndBlankDates() {
        when(movementService.getFiltered(eq(null), eq(null), eq(null), isNull(), isNull())).thenReturn(List.of(movement));
        when(movementService.getFiltered(eq(2L), eq(null), eq(null), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(movement));

        assertEquals(1, controller.getFiltered(null, null, null, "", " ").size());
        assertEquals(1, controller.getFiltered(2L, null, null, "2026-05-01T10:15:30", "2026-05-02T11:00:00").size());
    }
}
