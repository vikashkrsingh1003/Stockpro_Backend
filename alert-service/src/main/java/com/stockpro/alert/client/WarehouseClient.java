package com.stockpro.alert.client;

import com.stockpro.alert.config.FeignConfig;
import com.stockpro.alert.dto.WarehouseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "warehouse-service", configuration = FeignConfig.class)
public interface WarehouseClient {

    @GetMapping("/api/v1/warehouses/{id}")
    WarehouseDTO getWarehouseById(@PathVariable("id") Long id);
}
