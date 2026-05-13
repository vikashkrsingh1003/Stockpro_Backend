package com.stockpro.analytics.service.impl;

import com.stockpro.analytics.client.ProductClient;
import com.stockpro.analytics.client.WarehouseClient;
import com.stockpro.analytics.dto.*;
import com.stockpro.analytics.entity.ProductPerformance;
import com.stockpro.analytics.entity.SupplierSpend;
import com.stockpro.analytics.repository.ProductPerformanceRepository;
import com.stockpro.analytics.repository.SnapshotRepository;
import com.stockpro.analytics.repository.SupplierSpendRepository;
import com.stockpro.analytics.service.AnalyticsService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private final ProductClient productClient;
    private final WarehouseClient warehouseClient;
    private final ProductPerformanceRepository performanceRepository;
    private final SnapshotRepository snapshotRepository;
    private final SupplierSpendRepository supplierSpendRepository;

    // ─────────────────────────────────────────────────────────────
    // 1. GLOBAL INVENTORY VALUATION
    // ─────────────────────────────────────────────────────────────
    @Override
    public Double calculateGlobalValuation() {
        try {
            List<ProductDTO> products = productClient.getAllProducts();
            if (products == null) return 0.0;

            Map<Long, Double> productCosts = products.stream()
                    .filter(p -> p.getId() != null)
                    .collect(Collectors.toMap(
                            ProductDTO::getId,
                            p -> p.getCostPrice() != null ? p.getCostPrice() : 0.0,
                            (v1, v2) -> v1 // Handle duplicate IDs if any
                    ));

            List<WarehouseDTO> warehouses = warehouseClient.getAllWarehouses();
            if (warehouses == null) return 0.0;

            return warehouses.stream()
                    .flatMap(w -> {
                        try {
                            List<StockLevelDTO> inv = warehouseClient.getWarehouseInventory(w.getId());
                            return inv != null ? inv.stream() : Stream.<StockLevelDTO>empty();
                        } catch (Exception e) {
                            return Stream.empty();
                        }
                    })
                    .mapToDouble(stock -> {
                        int qty = stock.getQuantity() != null ? stock.getQuantity() : 0;
                        double cost = productCosts.getOrDefault(stock.getProductId(), 0.0);
                        return qty * cost;
                    })
                    .sum();

        } catch (Exception e) {
            log.error("[Analytics] Valuation failed: {}", e.getMessage(), e);
            return 0.0;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. TOP MOVING PRODUCTS
    // ─────────────────────────────────────────────────────────────
    @Override
    public List<ProductPerformanceDTO> getTopMovingProducts(int limit) {
        try {
            return performanceRepository.findTop10ByOrderByTurnoverRateDesc()
                    .stream().limit(limit).map(this::toDTO).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("[Analytics] Failed to fetch top moving products: {}", e.getMessage());
            return List.of();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. DEAD STOCK
    // ─────────────────────────────────────────────────────────────
    @Override
    public List<ProductPerformanceDTO> getDeadStock() {
        try {
            return performanceRepository.findByMovementCategory("DEAD")
                    .stream().map(this::toDTO).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("[Analytics] Failed to fetch dead stock: {}", e.getMessage());
            return List.of();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. DASHBOARD METRICS (single call)
    // ─────────────────────────────────────────────────────────────
    @Override
    public GlobalMetricsDTO getGlobalDashboardMetrics() {
        GlobalMetricsDTO metrics = new GlobalMetricsDTO();

        // ── 1. Total inventory valuation ──────────────────────────
        try {
            metrics.setTotalInventoryValue(calculateGlobalValuation());
        } catch (Exception e) {
            log.warn("[Analytics] Valuation skipped: {}", e.getMessage());
            metrics.setTotalInventoryValue(0.0);
        }

        // ── 2. Product count (from product-service) ───────────────
        try {
            List<ProductDTO> products = productClient.getAllProducts();
            metrics.setTotalProducts(products != null ? products.size() : 0);
        } catch (Exception e) {
            log.warn("[Analytics] Product count skipped: {}", e.getMessage());
            metrics.setTotalProducts(0);
        }

        // ── 3. Warehouse count + utilization ──────────────────────
        try {
            List<WarehouseDTO> warehouses = warehouseClient.getAllWarehouses();
            metrics.setTotalWarehouses(warehouses != null ? warehouses.size() : 0);

            Map<String, Double> utilizationMap = new HashMap<>();
            int highCapacityCount = 0;

            if (warehouses != null) {
                for (WarehouseDTO w : warehouses) {
                    if (w.getName() == null) continue;
                    int capacity = w.getCapacity() != null ? w.getCapacity() : 0;
                    int used = w.getUsedCapacity() != null ? w.getUsedCapacity() : 0;
                    double usage = (capacity > 0) ? ((double) used / capacity) * 100 : 0.0;
                    double rounded = Math.round(usage * 100.0) / 100.0;
                    utilizationMap.put(w.getName(), rounded);
                    if (rounded > 90.0) highCapacityCount++;
                }
            }
            metrics.setWarehouseUtilization(utilizationMap);
            metrics.setLowStockAlerts(highCapacityCount);
        } catch (Exception e) {
            log.warn("[Analytics] Warehouse data skipped: {}", e.getMessage());
            metrics.setTotalWarehouses(0);
            metrics.setWarehouseUtilization(new HashMap<>());
        }

        // ── 4. Product performance (from local analytics DB) ──────
        try {
            List<ProductPerformance> allPerf = performanceRepository.findAll();
            metrics.setTopMovingCount(allPerf.stream().filter(p -> "TOP_MOVING".equals(p.getMovementCategory())).count());
            metrics.setSlowMovingCount(allPerf.stream().filter(p -> "SLOW_MOVING".equals(p.getMovementCategory())).count());
            metrics.setDeadStockCount(allPerf.stream().filter(p -> "DEAD".equals(p.getMovementCategory())).count());
            metrics.setTopMovingProducts(getTopMovingProducts(5));
        } catch (Exception e) {
            log.warn("[Analytics] Performance data skipped: {}", e.getMessage());
        }

        return metrics;
    }

    // ─────────────────────────────────────────────────────────────
    // 5. WAREHOUSE UTILIZATION — Dedicated Endpoint (PDF §2.8)
    // ─────────────────────────────────────────────────────────────
    @Override
    public List<WarehouseUtilizationDTO> getWarehouseUtilization() {
        return warehouseClient.getAllWarehouses().stream().map(w -> {
            double pct = (w.getCapacity() != null && w.getCapacity() > 0)
                    ? ((double) w.getUsedCapacity() / w.getCapacity()) * 100 : 0.0;
            double rounded = Math.round(pct * 100.0) / 100.0;
            String status = rounded >= 90 ? "CRITICAL" : rounded >= 70 ? "HIGH" : "NORMAL";
            return new WarehouseUtilizationDTO(
                    w.getId(), w.getName(),
                    w.getUsedCapacity() != null ? w.getUsedCapacity() : 0,
                    w.getCapacity() != null ? w.getCapacity() : 0,
                    rounded, status
            );
        }).collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────
    // 6. SUPPLIER SPEND ANALYTICS (PDF §2.8)
    // ─────────────────────────────────────────────────────────────
    @Override
    public List<SupplierSpendDTO> getSupplierSpend() {
        return supplierSpendRepository.findTopSuppliersBySpend()
                .stream()
                .map(s -> new SupplierSpendDTO(
                        s.getSupplierId(),
                        s.getSupplierName(),
                        s.getTotalSpend(),
                        s.getTotalOrdersReceived(),
                        s.getAvgOrderValue(),
                        s.getLastPurchaseDate() != null ? s.getLastPurchaseDate().toString() : "N/A"
                ))
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────
    // 7. CSV EXPORTS (PDF §2.8)
    // ─────────────────────────────────────────────────────────────
    @Override
    public void exportValuationCsv(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"inventory_valuation.csv\"");

        try (PrintWriter writer = response.getWriter();
             CSVPrinter csv = new CSVPrinter(writer,
                     CSVFormat.DEFAULT.withHeader("Product ID", "SKU", "Quantity", "Cost Price", "Total Value"))) {

            Map<Long, Double> costs = productClient.getAllProducts()
                    .stream().collect(Collectors.toMap(ProductDTO::getId, ProductDTO::getCostPrice));
            Map<Long, String> skus = productClient.getAllProducts()
                    .stream().collect(Collectors.toMap(ProductDTO::getId, p -> p.getSku() != null ? p.getSku() : ""));

            warehouseClient.getAllWarehouses().stream()
                    .flatMap(w -> warehouseClient.getWarehouseInventory(w.getId()).stream())
                    .forEach(stock -> {
                        try {
                            double cost = costs.getOrDefault(stock.getProductId(), 0.0);
                            double value = stock.getQuantity() * cost;
                            csv.printRecord(
                                    stock.getProductId(),
                                    skus.getOrDefault(stock.getProductId(), ""),
                                    stock.getQuantity(),
                                    cost,
                                    Math.round(value * 100.0) / 100.0
                            );
                        } catch (IOException e) {
                            log.error("CSV write error: {}", e.getMessage());
                        }
                    });
        }
    }

    @Override
    public void exportDeadStockCsv(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"dead_stock_report.csv\"");

        try (PrintWriter writer = response.getWriter();
             CSVPrinter csv = new CSVPrinter(writer,
                     CSVFormat.DEFAULT.withHeader("Product ID", "SKU", "Turnover Rate", "Category", "Last Updated"))) {

            for (ProductPerformance p : performanceRepository.findByMovementCategory("DEAD")) {
                csv.printRecord(
                        p.getProductId(),
                        p.getSku() != null ? p.getSku() : "",
                        p.getTurnoverRate(),
                        p.getMovementCategory(),
                        p.getLastCalculated()
                );
            }
        }
    }

    @Override
    public void exportTopMovingCsv(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"top_moving_products.csv\"");

        try (PrintWriter writer = response.getWriter();
             CSVPrinter csv = new CSVPrinter(writer,
                     CSVFormat.DEFAULT.withHeader("Product ID", "SKU", "Turnover Rate", "Category", "Last Updated"))) {

            for (ProductPerformance p : performanceRepository.findTop10ByOrderByTurnoverRateDesc()) {
                csv.printRecord(
                        p.getProductId(),
                        p.getSku() != null ? p.getSku() : "",
                        p.getTurnoverRate(),
                        p.getMovementCategory(),
                        p.getLastCalculated()
                );
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // HELPER
    // ─────────────────────────────────────────────────────────────
    private ProductPerformanceDTO toDTO(ProductPerformance p) {
        return new ProductPerformanceDTO(
                p.getProductId(), p.getSku(),
                p.getTurnoverRate(), p.getMovementCategory(), p.getLastCalculated()
        );
    }
}
