package com.stockpro.purchase.publisher;

import com.stockpro.purchase.config.RabbitMQConfig;
import com.stockpro.purchase.dto.PurchaseOrderEvent;
import com.stockpro.purchase.entity.POStatus;
import com.stockpro.purchase.entity.PurchaseOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void publishSendsPurchaseOrderEvent() {
        PurchaseOrderEventPublisher publisher = new PurchaseOrderEventPublisher(rabbitTemplate);
        PurchaseOrder po = PurchaseOrder.builder()
                .id(10L)
                .referenceNumber("PO-10")
                .supplierId(3L)
                .warehouseId(4L)
                .totalAmount(500.0)
                .status(POStatus.RECEIVED)
                .build();
        ArgumentCaptor<PurchaseOrderEvent> eventCaptor = ArgumentCaptor.forClass(PurchaseOrderEvent.class);

        publisher.publish(po, RabbitMQConfig.PO_RECEIVED_ROUTING_KEY);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE),
                eq(RabbitMQConfig.PO_RECEIVED_ROUTING_KEY),
                eventCaptor.capture()
        );
        assertEquals("PO-10", eventCaptor.getValue().getReferenceNumber());
        assertEquals("RECEIVED", eventCaptor.getValue().getStatus());
    }

    @Test
    void publishSwallowsRabbitFailure() {
        PurchaseOrderEventPublisher publisher = new PurchaseOrderEventPublisher(rabbitTemplate);
        PurchaseOrder po = PurchaseOrder.builder()
                .referenceNumber("PO-11")
                .status(POStatus.APPROVED)
                .build();
        doThrow(new RuntimeException("rabbit down")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(PurchaseOrderEvent.class));

        assertDoesNotThrow(() -> publisher.publish(po, RabbitMQConfig.PO_APPROVED_ROUTING_KEY));
    }
}
