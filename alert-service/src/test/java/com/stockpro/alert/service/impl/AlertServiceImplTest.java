package com.stockpro.alert.service.impl;

import com.stockpro.alert.client.AuthClient;
import com.stockpro.alert.client.ProductClient;
import com.stockpro.alert.client.WarehouseClient;
import com.stockpro.alert.entity.Alert;
import com.stockpro.alert.entity.AlertSeverity;
import com.stockpro.alert.entity.AlertType;
import com.stockpro.alert.repository.AlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceImplTest {

    @Mock
    private AlertRepository repo;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private AuthClient authClient;

    @Mock
    private ProductClient productClient;

    @Mock
    private WarehouseClient warehouseClient;

    @InjectMocks
    private AlertServiceImpl service;

    private Alert alert;

    @BeforeEach
    void setup() {
        alert = Alert.builder()
                .id(10L)
                .alertType(AlertType.LOW_STOCK)
                .severity(AlertSeverity.CRITICAL)
                .title("Low stock")
                .message("Only 5 left")
                .productId(1L)
                .warehouseId(2L)
                .isRead(false)
                .acknowledged(false)
                .build();
    }

    @Test
    void createAlert_success_savesAndSendsCriticalMail() {
        when(repo.existsByProductIdAndWarehouseIdAndAlertTypeAndAcknowledgedFalse(1L, 2L, AlertType.LOW_STOCK))
                .thenReturn(false);
        when(repo.save(any(Alert.class))).thenReturn(alert);
        when(authClient.getAlertEmailRecipients()).thenReturn(List.of("manager@test.com", "staff@test.com"));
        when(mailSender.createMimeMessage()).thenReturn(newMimeMessage(), newMimeMessage());

        Alert saved = service.createAlert(AlertType.LOW_STOCK, AlertSeverity.CRITICAL,
                "Low stock", "Only 5 left", 1L, 2L, null);

        assertEquals(10L, saved.getId());
        verify(repo).save(any(Alert.class));
        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void createAlert_suppressesDuplicateActiveProductAlert() {
        when(repo.existsByProductIdAndWarehouseIdAndAlertTypeAndAcknowledgedFalse(1L, 2L, AlertType.LOW_STOCK))
                .thenReturn(true);

        Alert result = service.createAlert(AlertType.LOW_STOCK, AlertSeverity.CRITICAL,
                "Low stock", "Only 5 left", 1L, 2L, null);

        assertNull(result);
        verify(repo, never()).save(any());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void createAlert_warningDoesNotSendMail() {
        when(repo.save(any(Alert.class))).thenReturn(alert);

        service.createAlert(AlertType.PO_PENDING, AlertSeverity.WARNING,
                "PO pending", "Review needed", null, null, 5L);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    private MimeMessage newMimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    @Test
    void getAllAlerts_success() {
        when(repo.findAll()).thenReturn(List.of(alert));

        assertEquals(1, service.getAllAlerts().size());
    }

    @Test
    void getFilteredAlerts_typeSeverityAcknowledged() {
        when(repo.findByAlertTypeAndSeverityAndAcknowledged(AlertType.LOW_STOCK, AlertSeverity.CRITICAL, false))
                .thenReturn(List.of(alert));

        assertEquals(1, service.getFilteredAlerts("LOW_STOCK", "CRITICAL", false).size());
    }

    @Test
    void getFilteredAlerts_typeSeverityOnly() {
        when(repo.findByAlertTypeAndSeverity(AlertType.LOW_STOCK, AlertSeverity.CRITICAL))
                .thenReturn(List.of(alert));

        assertEquals(1, service.getFilteredAlerts("LOW_STOCK", "CRITICAL", null).size());
    }

    @Test
    void getFilteredAlerts_typeAcknowledgedOnly() {
        when(repo.findByAlertTypeAndAcknowledged(AlertType.LOW_STOCK, false)).thenReturn(List.of(alert));

        assertEquals(1, service.getFilteredAlerts("LOW_STOCK", null, false).size());
    }

    @Test
    void getFilteredAlerts_severityAcknowledgedOnly() {
        when(repo.findBySeverityAndAcknowledged(AlertSeverity.CRITICAL, false)).thenReturn(List.of(alert));

        assertEquals(1, service.getFilteredAlerts(null, "CRITICAL", false).size());
    }

    @Test
    void getFilteredAlerts_typeOnly() {
        when(repo.findByAlertType(AlertType.LOW_STOCK)).thenReturn(List.of(alert));

        assertEquals(1, service.getFilteredAlerts("LOW_STOCK", null, null).size());
    }

    @Test
    void getFilteredAlerts_severityOnly() {
        when(repo.findBySeverity(AlertSeverity.CRITICAL)).thenReturn(List.of(alert));

        assertEquals(1, service.getFilteredAlerts(null, "CRITICAL", null).size());
    }

    @Test
    void getFilteredAlerts_acknowledgedOnly() {
        when(repo.findByAcknowledged(false)).thenReturn(List.of(alert));

        assertEquals(1, service.getFilteredAlerts(null, null, false).size());
    }

    @Test
    void getFilteredAlerts_noFilters() {
        when(repo.findAll()).thenReturn(List.of(alert));

        assertEquals(1, service.getFilteredAlerts(null, null, null).size());
    }

    @Test
    void acknowledgeAlert_success() {
        when(repo.findById(10L)).thenReturn(Optional.of(alert));
        when(repo.save(alert)).thenReturn(alert);

        Alert saved = service.acknowledgeAlert(10L);

        assertTrue(saved.getAcknowledged());
        assertTrue(saved.getIsRead());
        assertNotNull(saved.getAcknowledgedAt());
    }

    @Test
    void acknowledgeAlert_notFound() {
        when(repo.findById(10L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.acknowledgeAlert(10L));
    }

    @Test
    void getUnreadCount_success() {
        when(repo.countByIsReadFalse()).thenReturn(3L);

        assertEquals(3L, service.getUnreadCount());
    }

    @Test
    void markAsRead_success() {
        when(repo.findById(10L)).thenReturn(Optional.of(alert));
        when(repo.save(alert)).thenReturn(alert);

        Alert saved = service.markAsRead(10L);

        assertTrue(saved.getIsRead());
    }

    @Test
    void markAsRead_notFound() {
        when(repo.findById(10L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.markAsRead(10L));
    }
}
