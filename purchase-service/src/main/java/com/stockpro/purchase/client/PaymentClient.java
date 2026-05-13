package com.stockpro.purchase.client;

import com.stockpro.purchase.dto.PaymentReportRequest;
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
public class PaymentClient {

    private final RestTemplate restTemplate;

    @Value("${stockpro.payment-service.base-url:http://localhost:8089/api/v1/payments}")
    private String paymentServiceBaseUrl;

    @Value("${stockpro.internal.service-token:stockpro-internal-token}")
    private String internalServiceToken;

    public void generatePaymentReport(PaymentReportRequest request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service-Token", internalServiceToken);
            restTemplate.postForEntity(
                    paymentServiceBaseUrl + "/internal/reports",
                    new HttpEntity<>(request, headers),
                    Object.class
            );
        } catch (RestClientException ex) {
            log.warn("Unable to generate payment report for poId {}: {}", request.getPoId(), ex.getMessage());
        }
    }
}
