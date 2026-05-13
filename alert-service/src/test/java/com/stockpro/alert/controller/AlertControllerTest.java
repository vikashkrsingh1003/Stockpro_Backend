package com.stockpro.alert.controller;

import com.stockpro.alert.entity.Alert;
import com.stockpro.alert.entity.AlertSeverity;
import com.stockpro.alert.entity.AlertType;
import com.stockpro.alert.service.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertControllerTest {

    @Mock
    private AlertService service;

    private AlertController controller;
    private Alert alert;

    @BeforeEach
    void setup() {
        controller = new AlertController(service);
        alert = Alert.builder()
                .id(1L)
                .alertType(AlertType.LOW_STOCK)
                .severity(AlertSeverity.CRITICAL)
                .title("Low stock")
                .build();
    }

    @Test
    void getAllDelegatesToFilteredAlerts() {
        when(service.getFilteredAlerts("LOW_STOCK", "CRITICAL", false)).thenReturn(List.of(alert));

        assertEquals(1, controller.getAll("LOW_STOCK", "CRITICAL", false).size());
    }

    @Test
    void ackReadAndCountDelegateToService() {
        when(service.acknowledgeAlert(1L)).thenReturn(alert);
        when(service.markAsRead(1L)).thenReturn(alert);
        when(service.getUnreadCount()).thenReturn(3L);

        assertSame(alert, controller.ack(1L));
        assertSame(alert, controller.read(1L));
        assertEquals(3L, controller.count());
    }
}
