package com.stockpro.alert.listerner;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.stockpro.alert.dto.PurchaseOrderEvent;
import com.stockpro.alert.dto.StockAlertEvent;
import com.stockpro.alert.entity.AlertSeverity;
import com.stockpro.alert.entity.AlertType;
import com.stockpro.alert.service.AlertService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class AlertEventListener {

    private final AlertService alertService;

    @RabbitListener(queues = "alert.stock.low.queue")
    public void lowStock(StockAlertEvent e) {
        log.info(" [RabbitMQ] Received LOW STOCK event: productId={}, qty={}",
                e.getProductId(), e.getCurrentQty());
        try {
            alertService.createAlert(
                    AlertType.LOW_STOCK,
                    AlertSeverity.CRITICAL,
                    "Low Stock Alert",
                    "Product #" + e.getProductId() + " in warehouse #" + e.getWarehouseId()
                            + " is below 20 units. Current stock: " + e.getCurrentQty(),
                    e.getProductId(),
                    e.getWarehouseId(),
                    null
            );
            log.info(" LOW_STOCK alert saved for product {}", e.getProductId());
        } catch (Exception ex) {
            log.error(" Failed to create LOW_STOCK alert: {}", ex.getMessage(), ex);
        }
    }

    //  PDF 2.7 — OVERSTOCK alert (WARNING severity)
    @RabbitListener(queues = "alert.stock.high.queue")
    public void overStock(StockAlertEvent e) {
        log.info(" [RabbitMQ] Received OVERSTOCK event: productId={}, qty={}",
                e.getProductId(), e.getCurrentQty());
        try {
            alertService.createAlert(
                    AlertType.OVERSTOCK,
                    AlertSeverity.WARNING,
                    "Overstock Alert",
                    "Product #" + e.getProductId() + " exceeds max level: " + e.getCurrentQty() + " units",
                    e.getProductId(),
                    e.getWarehouseId(),
                    null
            );
            log.info(" OVERSTOCK alert saved for product {}", e.getProductId());
        } catch (Exception ex) {
            log.error(" Failed to create OVERSTOCK alert: {}", ex.getMessage(), ex);
        }
    }

    @RabbitListener(queues = "alert.po.pending.queue")
    public void poPending(PurchaseOrderEvent e) {
        alertService.createAlert(
                AlertType.PO_PENDING,
                AlertSeverity.INFO,
                "PO Pending",
                "PO " + e.getReferenceNumber() + " pending",
                null,
                e.getWarehouseId(),
                e.getPoId()
        );
    }
}
