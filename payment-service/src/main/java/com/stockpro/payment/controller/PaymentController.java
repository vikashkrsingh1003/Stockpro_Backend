package com.stockpro.payment.controller;

import com.stockpro.payment.dto.PaymentRequest;
import com.stockpro.payment.dto.PaymentReportRequest;
import com.stockpro.payment.dto.PaymentResponse;
import com.stockpro.payment.dto.PaymentVerificationRequest;
import com.stockpro.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payment Controller", description = "Razorpay payment APIs for StockPro purchase orders")
public class PaymentController {

    private final PaymentService paymentService;

    @Value("${stockpro.internal.service-token:stockpro-internal-token}")
    private String internalServiceToken;

    @PostMapping("/initiate")
    @Operation(summary = "Create a Razorpay order and store a pending payment")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<PaymentResponse> initiatePayment(@Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.ok(paymentService.initiatePayment(request));
    }

    @PostMapping("/internal/reports")
    @Operation(summary = "Internal endpoint: generate payment report after PO receipt")
    public ResponseEntity<PaymentResponse> generatePaymentReport(@RequestHeader("X-Internal-Service-Token") String token,
                                                                 @Valid @RequestBody PaymentReportRequest request) {
        if (!internalServiceToken.equals(token)) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(paymentService.generatePaymentReport(request));
    }

    @GetMapping("/reports")
    @Operation(summary = "Get payment reports")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<List<PaymentResponse>> getPaymentReports() {
        return ResponseEntity.ok(paymentService.getPaymentReports());
    }

    @PostMapping("/reports/{paymentId}/process")
    @Operation(summary = "Process supplier payment report and create Razorpay order")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<PaymentResponse> processPaymentReport(@PathVariable Long paymentId) {
        return ResponseEntity.ok(paymentService.processPaymentReport(paymentId));
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify Razorpay checkout payment signature and update payment status")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<PaymentResponse> verifyPayment(@Valid @RequestBody PaymentVerificationRequest request) {
        return ResponseEntity.ok(paymentService.verifyPayment(request));
    }

    @GetMapping("/{poId}")
    @Operation(summary = "Get latest payment status by purchase order id")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<PaymentResponse> getPaymentStatus(@PathVariable Long poId) {
        return ResponseEntity.ok(paymentService.getLatestPaymentByPoId(poId));
    }
}
