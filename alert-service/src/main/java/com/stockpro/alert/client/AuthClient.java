package com.stockpro.alert.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(name = "authservice", configuration = com.stockpro.alert.config.FeignConfig.class)
public interface AuthClient {

    @GetMapping("/api/v1/auth/internal/alert-emails")
    List<String> getAlertEmailRecipients();
}
