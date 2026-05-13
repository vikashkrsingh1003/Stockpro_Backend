package com.stockpro.purchase.controller;

import com.stockpro.purchase.entity.PurchaseOrder;
import com.stockpro.purchase.dto.PaymentStatusUpdateRequest;
import com.stockpro.purchase.service.PurchaseOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/purchase-orders")
@RequiredArgsConstructor
@Tag(name = "Purchase Order Controller", description = "Endpoints for managing the procurement lifecycle")
public class PurchaseOrderController {

    private final PurchaseOrderService poService;

    @Value("${stockpro.internal.service-token:stockpro-internal-token}")
    private String internalServiceToken;

    @PostMapping
    @Operation(summary = "Create a new Purchase Order (Draft)")
    @PreAuthorize("hasRole('OFFICER')")
    public ResponseEntity<PurchaseOrder> createPO(@RequestBody PurchaseOrder po) {
        return ResponseEntity.ok(poService.createPO(po));
    }

    @PutMapping("/{id}/submit")
    @Operation(summary = "Submit a PO for approval")
    @PreAuthorize("hasRole('OFFICER')")
    public ResponseEntity<PurchaseOrder> submitPO(@PathVariable Long id) {
        return ResponseEntity.ok(poService.submitPO(id));
    }

    @PutMapping("/{id}/approve")
    @Operation(summary = "Approve a pending PO")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PurchaseOrder> approvePO(@PathVariable Long id) {
        return ResponseEntity.ok(poService.approvePO(id));
    }

    @PutMapping("/{id}/reject")
    @Operation(summary = "Reject a pending PO with reason")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PurchaseOrder> rejectPO(@PathVariable Long id, @RequestParam String reason) {
        return ResponseEntity.ok(poService.rejectPO(id, reason));
    }

    @PostMapping("/{id}/receive")
    @Operation(summary = "Record goods receipt (triggers stock update)")
    @PreAuthorize("hasAnyRole('OFFICER', 'STAFF')")
    public ResponseEntity<PurchaseOrder> receiveGoods(@PathVariable Long id,
                                                     @RequestParam Long productId,
                                                     @RequestParam Integer receivedQty) {
        return ResponseEntity.ok(poService.receiveGoods(id, productId, receivedQty));
    }

    @GetMapping
    @Operation(summary = "Get filtered list of Purchase Orders")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OFFICER', 'STAFF')")
    public ResponseEntity<List<PurchaseOrder>> getPOs(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        return ResponseEntity.ok(poService.getPOs(supplierId, warehouseId, status, startDate, endDate));
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancel an existing PO")
    @PreAuthorize("hasRole('OFFICER')")
    public ResponseEntity<PurchaseOrder> cancelPO(@PathVariable Long id, @RequestParam String reason) {
        return ResponseEntity.ok(poService.cancelPO(id, reason));
    }

    @PutMapping("/{id}/payment-status")
    @Operation(summary = "Internal endpoint: update payment status from payment-service")
    public ResponseEntity<PurchaseOrder> updatePaymentStatus(@PathVariable Long id,
                                                            @RequestHeader("X-Internal-Service-Token") String token,
                                                            @RequestBody PaymentStatusUpdateRequest request) {
        if (!internalServiceToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal service token");
        }
        return ResponseEntity.ok(poService.updatePaymentStatus(
                id,
                request.getPaymentStatus(),
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId()
        ));
    }

    // Called by alert-service scheduler to detect overdue POs
    @GetMapping("/overdue")
    @Operation(summary = "Get all overdue POs (approved but past expected delivery date)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OFFICER')")
    public ResponseEntity<List<PurchaseOrder>> getOverduePOs() {
        List<PurchaseOrder> overdue = poService.getPOs(null, null, "APPROVED", null, null)
                .stream()
                .filter(po -> po.getExpectedDeliveryDate() != null
                        && po.getExpectedDeliveryDate().isBefore(LocalDateTime.now()))
                .toList();
        return ResponseEntity.ok(overdue);
    }
}
