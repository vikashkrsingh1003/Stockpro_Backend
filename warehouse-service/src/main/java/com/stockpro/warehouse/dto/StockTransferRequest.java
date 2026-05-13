package com.stockpro.warehouse.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StockTransferRequest {

    @NotNull
    private Long productId;

    @NotNull
    private Long fromWarehouse;

    @NotNull
    private Long toWarehouse;

    @NotNull
    @Min(1)
    private Integer qty;

    private String reason;
}
