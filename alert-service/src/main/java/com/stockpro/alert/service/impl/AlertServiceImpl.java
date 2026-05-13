package com.stockpro.alert.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

import com.stockpro.alert.client.AuthClient;
import com.stockpro.alert.client.ProductClient;
import com.stockpro.alert.client.WarehouseClient;
import com.stockpro.alert.dto.ProductDTO;
import com.stockpro.alert.dto.WarehouseDTO;
import com.stockpro.alert.entity.Alert;
import com.stockpro.alert.entity.AlertSeverity;
import com.stockpro.alert.entity.AlertType;
import com.stockpro.alert.repository.AlertRepository;
import com.stockpro.alert.service.AlertService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertServiceImpl implements AlertService {

    private final AlertRepository repo;
    private final JavaMailSender mailSender;
    private final AuthClient authClient;
    private final ProductClient productClient;
    private final WarehouseClient warehouseClient;

    @Value("${stockpro.alert.email-to:vksingh1062003@gmail.com}")
    private String alertEmailTo;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${stockpro.frontend.dashboard-url:http://localhost:4200/dashboard}")
    private String dashboardUrl;

    @Override
    public Alert createAlert(AlertType type, AlertSeverity severity,
                             String title, String message,
                             Long productId, Long warehouseId, Long poId) {

        // Deduplication: don't create duplicate active alerts for the same product + type
        if (productId != null &&
            repo.existsByProductIdAndWarehouseIdAndAlertTypeAndAcknowledgedFalse(productId, warehouseId, type)) {
            log.info(" Duplicate alert suppressed for product {} warehouse {} type {}", productId, warehouseId, type);
            return null;
        }

        String displayMessage = buildDisplayMessage(type, message, productId, warehouseId);

        Alert alert = Alert.builder()
                .alertType(type)
                .severity(severity)
                .title(title)
                .message(displayMessage)
                .productId(productId)
                .warehouseId(warehouseId)
                .poId(poId)
                .recipientRole("MANAGER")
                .isRead(false)
                .acknowledged(false)
                .createdAt(LocalDateTime.now())
                .build();

        Alert saved = repo.save(alert);
        log.info(" Alert created: [{}] {} - {}", severity, title, displayMessage);

        //  PDF 2.7: Send email only for CRITICAL alerts
        if (severity == AlertSeverity.CRITICAL) {
            sendEmail(title, displayMessage);
        }

        return saved;
    }

    @Override
    public List<Alert> getAllAlerts() {
        return repo.findAll();
    }

    @Override
    public List<Alert> getFilteredAlerts(String type, String severity, Boolean acknowledged) {
        AlertType alertType = (type != null) ? AlertType.valueOf(type.toUpperCase()) : null;
        AlertSeverity alertSeverity = (severity != null) ? AlertSeverity.valueOf(severity.toUpperCase()) : null;

        if (alertType != null && alertSeverity != null && acknowledged != null) {
            return repo.findByAlertTypeAndSeverityAndAcknowledged(alertType, alertSeverity, acknowledged);
        } else if (alertType != null && alertSeverity != null) {
            return repo.findByAlertTypeAndSeverity(alertType, alertSeverity);
        } else if (alertType != null && acknowledged != null) {
            return repo.findByAlertTypeAndAcknowledged(alertType, acknowledged);
        } else if (alertSeverity != null && acknowledged != null) {
            return repo.findBySeverityAndAcknowledged(alertSeverity, acknowledged);
        } else if (alertType != null) {
            return repo.findByAlertType(alertType);
        } else if (alertSeverity != null) {
            return repo.findBySeverity(alertSeverity);
        } else if (acknowledged != null) {
            return repo.findByAcknowledged(acknowledged);
        }

        return repo.findAll();
    }

    @Override
    public Alert acknowledgeAlert(Long id) {
        Alert alert = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Alert not found: " + id));

        alert.setAcknowledged(true);
        alert.setAcknowledgedAt(LocalDateTime.now());
        alert.setIsRead(true);

        return repo.save(alert);
    }

    @Override
    public long getUnreadCount() {
        return repo.countByIsReadFalse();
    }

    @Override
    public Alert markAsRead(Long id) {
        Alert alert = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Alert not found: " + id));

        alert.setIsRead(true);
        return repo.save(alert);
    }

    //  Email sender — only called for CRITICAL alerts (PDF 2.7)
    private void sendEmail(String subject, String body) {
        List<String> recipients = resolveAlertRecipients();
        if (recipients.isEmpty()) {
            log.warn("No alert email recipients found. Critical email skipped: {}", subject);
            return;
        }

        int sentCount = 0;
        for (String recipient : recipients) {
            try {
                MimeMessage mail = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mail, true, "UTF-8");
                if (fromEmail != null && !fromEmail.isBlank()) {
                    helper.setFrom(fromEmail);
                }
                helper.setTo(recipient);
                helper.setSubject("[CRITICAL ALERT] " + subject);
                helper.setText(buildPlainText(subject, body), buildAlertEmailHtml(subject, body));
                mailSender.send(mail);
                sentCount++;
            } catch (Exception e) {
                log.error("Failed to send critical alert email to {}: {}", recipient, e.getMessage());
            }
        }

        log.info("CRITICAL email sent to {}/{} registered users: {}", sentCount, recipients.size(), subject);
    }

    private List<String> resolveAlertRecipients() {
        try {
            List<String> users = authClient.getAlertEmailRecipients();
            List<String> recipients = normalizeEmails(users);
            if (!recipients.isEmpty()) {
                return recipients;
            }
            log.warn("Auth Service returned no active user emails. Falling back to configured alert email.");
        } catch (Exception e) {
            log.warn("Unable to fetch alert recipients from Auth Service: {}. Falling back to configured alert email.",
                    e.getMessage());
        }
        return normalizeEmails(List.of(alertEmailTo));
    }

    private List<String> normalizeEmails(List<String> emails) {
        if (emails == null) {
            return List.of();
        }
        return emails.stream()
                .filter(email -> email != null && !email.isBlank())
                .map(email -> email.trim().toLowerCase())
                .distinct()
                .toList();
    }

    private String buildPlainText(String subject, String body) {
        return "StockPro Critical Alert\n\n"
                + subject + "\n\n"
                + body + "\n\n"
                + "Open StockPro Dashboard: " + dashboardUrl + "\n\n"
                + "Please review this alert in StockPro.";
    }

    private String buildAlertEmailHtml(String subject, String body) {
        String safeSubject = escapeHtml(subject);
        String safeBody = escapeHtml(body);
        String generatedAt = LocalDateTime.now().toString().replace('T', ' ');

        return """
                <!doctype html>
                <html>
                  <body style="margin:0;padding:0;background:#f4f6fb;font-family:Arial,Helvetica,sans-serif;color:#111827;">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f4f6fb;padding:28px 12px;">
                      <tr>
                        <td align="center">
                          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:640px;background:#ffffff;border-radius:22px;overflow:hidden;border:1px solid #e5e7eb;box-shadow:0 18px 45px rgba(15,23,42,.12);">
                            <tr>
                              <td style="background:#0f172a;padding:24px 28px;color:#ffffff;">
                                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0">
                                  <tr>
                                    <td>
                                      <div style="font-size:13px;letter-spacing:2px;text-transform:uppercase;color:#c4b5fd;font-weight:700;">StockPro Inventory</div>
                                      <div style="font-size:28px;line-height:36px;font-weight:800;margin-top:8px;">Critical Stock Alert</div>
                                    </td>
                                    <td align="right" style="vertical-align:top;">
                                      <span style="display:inline-block;background:#fee2e2;color:#b91c1c;border-radius:999px;padding:8px 12px;font-size:12px;font-weight:800;letter-spacing:1px;text-transform:uppercase;">Critical</span>
                                    </td>
                                  </tr>
                                </table>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:28px;">
                                <div style="font-size:14px;letter-spacing:1.4px;text-transform:uppercase;color:#7c3aed;font-weight:800;">Low Stock Alert</div>
                                <h1 style="margin:10px 0 14px;font-size:26px;line-height:34px;color:#020617;">%s</h1>
                                <div style="background:#fff7ed;border:1px solid #fed7aa;border-radius:16px;padding:18px 20px;color:#7c2d12;font-size:16px;line-height:26px;font-weight:600;">%s</div>

                                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="margin-top:22px;">
                                  <tr>
                                    <td style="background:#f8fafc;border-radius:14px;padding:14px 16px;border:1px solid #e5e7eb;">
                                      <div style="font-size:12px;text-transform:uppercase;letter-spacing:1.5px;color:#94a3b8;font-weight:800;">Generated At</div>
                                      <div style="font-size:15px;color:#334155;font-weight:700;margin-top:6px;">%s</div>
                                    </td>
                                  </tr>
                                </table>

                                <div style="margin-top:26px;text-align:center;">
                                  <a href="%s" target="_blank" style="display:inline-block;background:#7c3aed;color:#ffffff;border-radius:14px;padding:13px 22px;font-size:14px;font-weight:800;text-decoration:none;">Open StockPro Dashboard</a>
                                </div>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:18px 28px;background:#f8fafc;border-top:1px solid #e5e7eb;color:#64748b;font-size:12px;line-height:18px;text-align:center;">
                                This automated notification was sent to active StockPro users. Please verify inventory and take action if required.
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """.formatted(safeSubject, safeBody, generatedAt, escapeHtml(dashboardUrl));
    }

    private String buildDisplayMessage(AlertType type, String fallbackMessage, Long productId, Long warehouseId) {
        if (productId == null || warehouseId == null ||
                (type != AlertType.LOW_STOCK && type != AlertType.OVERSTOCK)) {
            return fallbackMessage;
        }

        String productName = resolveProductName(productId);
        String warehouseName = resolveWarehouseName(warehouseId);
        String stockText = extractCurrentStockText(fallbackMessage);

        if (type == AlertType.LOW_STOCK) {
            return productName + " in " + warehouseName + " is below 20 units. " + stockText;
        }

        return productName + " in " + warehouseName + " exceeds maximum stock level. " + stockText;
    }

    private String resolveProductName(Long productId) {
        try {
            ProductDTO product = productClient.getProductById(productId);
            if (product != null && product.getName() != null && !product.getName().isBlank()) {
                return product.getName();
            }
        } catch (Exception e) {
            log.warn("Unable to resolve product name for product {}: {}", productId, e.getMessage());
        }
        return "Product #" + productId;
    }

    private String resolveWarehouseName(Long warehouseId) {
        try {
            WarehouseDTO warehouse = warehouseClient.getWarehouseById(warehouseId);
            if (warehouse != null && warehouse.getName() != null && !warehouse.getName().isBlank()) {
                return warehouse.getName();
            }
        } catch (Exception e) {
            log.warn("Unable to resolve warehouse name for warehouse {}: {}", warehouseId, e.getMessage());
        }
        return "warehouse #" + warehouseId;
    }

    private String extractCurrentStockText(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        int currentStockIndex = message.toLowerCase().indexOf("current stock:");
        if (currentStockIndex >= 0) {
            return message.substring(currentStockIndex).trim();
        }
        return message;
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
