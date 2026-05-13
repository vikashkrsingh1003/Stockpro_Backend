package com.stockpro.warehouse.publisher;

import com.stockpro.warehouse.config.RabbitMQConfig;
import com.stockpro.warehouse.dto.StockMovementEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void publishStockMovementSendsEvent() {
        StockEventPublisher publisher = new StockEventPublisher(rabbitTemplate);
        ArgumentCaptor<StockMovementEvent> eventCaptor = ArgumentCaptor.forClass(StockMovementEvent.class);

        publisher.publishStockMovement(2L, 1L, 7, "IN", "received");

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE),
                eq(RabbitMQConfig.STOCK_MOVEMENT_ROUTING_KEY),
                eventCaptor.capture()
        );
        assertEquals(2L, eventCaptor.getValue().getProductId());
        assertEquals(7, eventCaptor.getValue().getQuantity());
    }

    @Test
    void publishStockAlertSendsMapAndSwallowsFailure() {
        StockEventPublisher publisher = new StockEventPublisher(rabbitTemplate);

        publisher.publishStockAlert(2L, 1L, 3, RabbitMQConfig.STOCK_LOW_ROUTING_KEY);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE),
                eq(RabbitMQConfig.STOCK_LOW_ROUTING_KEY),
                eventCaptor.capture()
        );
        assertEquals(3, eventCaptor.getValue().get("currentQty"));

        doThrow(new RuntimeException("rabbit down")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class));

        assertDoesNotThrow(() -> publisher.publishStockMovement(2L, 1L, 7, "OUT", "issue"));
        assertDoesNotThrow(() -> publisher.publishStockAlert(2L, 1L, 99, RabbitMQConfig.STOCK_HIGH_ROUTING_KEY));
    }
}
