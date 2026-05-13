package com.stockpro.stockmovement.client;

import com.stockpro.stockmovement.dto.StockTransferRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class WarehouseClient {

    private final RestTemplate restTemplate;

    @Value("${stockpro.warehouse-service.base-url:http://localhost:8083/api/v1/warehouses}")
    private String warehouseServiceBaseUrl;

    @Value("${stockpro.internal.service-token:stockpro-internal-token}")
    private String internalServiceToken;

    public void applyTransfer(StockTransferRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Service-Token", internalServiceToken);
        restTemplate.postForEntity(
                warehouseServiceBaseUrl + "/internal/transfer",
                new HttpEntity<>(request, headers),
                StockTransferRequest.class
        );
    }
}
