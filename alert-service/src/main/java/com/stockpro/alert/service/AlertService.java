package com.stockpro.alert.service;

import java.util.List;

import com.stockpro.alert.entity.Alert;
import com.stockpro.alert.entity.AlertSeverity;
import com.stockpro.alert.entity.AlertType;

public interface AlertService {

    Alert createAlert(AlertType type,
                      AlertSeverity severity,
                      String title,
                      String message,
                      Long productId,
                      Long warehouseId,
                      Long poId);

    List<Alert> getAllAlerts();

    //  filtered query
    List<Alert> getFilteredAlerts(String type, String severity, Boolean acknowledged);

    Alert acknowledgeAlert(Long id);

    long getUnreadCount();

    Alert markAsRead(Long id);
}