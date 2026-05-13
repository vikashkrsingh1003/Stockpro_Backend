package com.stockpro.payment.client;

import com.stockpro.payment.dto.PurchasePaymentStatusRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseServiceClient {

    private final RestTemplate restTemplate;

    @Value("${stockpro.purchase-service.base-url}")
    private String purchaseServiceBaseUrl;

    @Value("${stockpro.purchase-service.notify-enabled:true}")
    private boolean notifyEnabled;

    @Value("${stockpro.internal.service-token}")
    private String internalServiceToken;

    public void updatePaymentStatus(Long poId, String paymentStatus, String razorpayOrderId, String razorpayPaymentId) {
        if (!notifyEnabled) {
            return;
        }
        try {
            String url = purchaseServiceBaseUrl + "/" + poId + "/payment-status";
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service-Token", internalServiceToken);
            HttpEntity<PurchasePaymentStatusRequest> entity = new HttpEntity<>(
                    new PurchasePaymentStatusRequest(paymentStatus, razorpayOrderId, razorpayPaymentId),
                    headers
            );
            restTemplate.put(url, entity);
        } catch (RestClientException ex) {
            log.warn("Unable to notify purchase-service for poId {}: {}", poId, ex.getMessage());
        }
    }
}
