package com.stockpro.payment.service.impl;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import com.stockpro.payment.client.PurchaseServiceClient;
import com.stockpro.payment.config.RazorpayConfig;
import com.stockpro.payment.dto.PaymentRequest;
import com.stockpro.payment.dto.PaymentReportRequest;
import com.stockpro.payment.dto.PaymentResponse;
import com.stockpro.payment.dto.PaymentVerificationRequest;
import com.stockpro.payment.entity.Payment;
import com.stockpro.payment.entity.PaymentStatus;
import com.stockpro.payment.exception.InvalidPaymentSignatureException;
import com.stockpro.payment.exception.PaymentException;
import com.stockpro.payment.exception.PaymentNotFoundException;
import com.stockpro.payment.repository.PaymentRepository;
import com.stockpro.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final BigDecimal RAZORPAY_MAX_ORDER_AMOUNT_INR = new BigDecimal("500000.00");

    private final RazorpayClient razorpayClient;
    private final RazorpayConfig razorpayConfig;
    private final PaymentRepository paymentRepository;
    private final PurchaseServiceClient purchaseServiceClient;

    @Override
    @Transactional
    public PaymentResponse initiatePayment(PaymentRequest request) {
        Payment payment = createRazorpayOrder(
                request.getPoId(),
                request.getSupplierId(),
                request.getAmount(),
                request.getCurrency()
        );
        return toResponse(paymentRepository.save(payment));
    }

    @Override
    @Transactional
    public PaymentResponse generatePaymentReport(PaymentReportRequest request) {
        String currency = normalizeCurrency(request.getCurrency());
        Payment report = Payment.builder()
                .poId(request.getPoId())
                .supplierId(request.getSupplierId())
                .amount(request.getAmount().setScale(2, RoundingMode.HALF_UP))
                .currency(currency)
                .razorpayOrderId("REPORT-" + request.getPoId() + "-" + System.currentTimeMillis())
                .productDetails(request.getProductDetails())
                .status(PaymentStatus.PENDING)
                .build();

        return toResponse(paymentRepository.save(report));
    }

    @Override
    @Transactional
    public PaymentResponse processPaymentReport(Long paymentId) {
        Payment report = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment report not found"));

        if (report.getStatus() == PaymentStatus.SUCCESS) {
            return toResponse(report);
        }

        Payment processed = createRazorpayOrder(
                report.getPoId(),
                report.getSupplierId(),
                report.getAmount(),
                report.getCurrency()
        );
        report.setRazorpayOrderId(processed.getRazorpayOrderId());
        report.setStatus(PaymentStatus.PENDING);
        return toResponse(paymentRepository.save(report));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentReports() {
        return paymentRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private Payment createRazorpayOrder(Long poId, Long supplierId, BigDecimal amount, String requestedCurrency) {
        try {
            String currency = normalizeCurrency(requestedCurrency);
            validateRazorpayAmount(amount, currency);
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", toMinorUnits(amount));
            orderRequest.put("currency", currency);
            orderRequest.put("receipt", "po_" + poId + "_" + System.currentTimeMillis());
            orderRequest.put("payment_capture", 1);

            Order razorpayOrder = razorpayClient.orders.create(orderRequest);
            String razorpayOrderId = razorpayOrder.get("id");

            return Payment.builder()
                    .poId(poId)
                    .supplierId(supplierId)
                    .amount(amount.setScale(2, RoundingMode.HALF_UP))
                    .currency(currency)
                    .razorpayOrderId(razorpayOrderId)
                    .status(PaymentStatus.PENDING)
                    .build();
        } catch (RazorpayException ex) {
            throw new PaymentException("Unable to create Razorpay order: " + ex.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    @Override
    @Transactional
    public PaymentResponse verifyPayment(PaymentVerificationRequest request) {
        Payment payment = paymentRepository.findByRazorpayOrderId(request.getRazorpayOrderId())
                .orElseThrow(() -> new PaymentNotFoundException("Payment order not found"));

        boolean valid = verifyCheckoutSignature(request);
        payment.setRazorpayPaymentId(request.getRazorpayPaymentId());
        payment.setRazorpaySignature(request.getRazorpaySignature());
        payment.setStatus(valid ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);

        Payment saved = paymentRepository.save(payment);
        if (valid) {
            purchaseServiceClient.updatePaymentStatus(saved.getPoId(), "PAID", saved.getRazorpayOrderId(), saved.getRazorpayPaymentId());
            return toResponse(saved);
        }

        purchaseServiceClient.updatePaymentStatus(saved.getPoId(), "PAYMENT_FAILED", saved.getRazorpayOrderId(), saved.getRazorpayPaymentId());
        throw new InvalidPaymentSignatureException("Invalid Razorpay payment signature");
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getLatestPaymentByPoId(Long poId) {
        return paymentRepository.findTopByPoIdOrderByCreatedAtDesc(poId)
                .map(this::toResponse)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found for poId " + poId));
    }

    private boolean verifyCheckoutSignature(PaymentVerificationRequest request) {
        try {
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", request.getRazorpayOrderId());
            options.put("razorpay_payment_id", request.getRazorpayPaymentId());
            options.put("razorpay_signature", request.getRazorpaySignature());
            return Utils.verifyPaymentSignature(options, razorpayConfig.getKeySecret());
        } catch (RazorpayException ex) {
            return false;
        }
    }

    private int toMinorUnits(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    private void validateRazorpayAmount(BigDecimal amount, String currency) {
        if ("INR".equals(currency) && amount.compareTo(RAZORPAY_MAX_ORDER_AMOUNT_INR) > 0) {
            throw new PaymentException(
                    "This payable amount is INR " + amount.setScale(2, RoundingMode.HALF_UP)
                            + ", which is above the Razorpay single-order limit of INR "
                            + RAZORPAY_MAX_ORDER_AMOUNT_INR.setScale(2, RoundingMode.HALF_UP)
                            + ". Please split this supplier payment into smaller payments.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private String normalizeCurrency(String currency) {
        return currency == null || currency.isBlank() ? "INR" : currency.trim().toUpperCase(Locale.ROOT);
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .poId(payment.getPoId())
                .supplierId(payment.getSupplierId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .razorpayOrderId(payment.getRazorpayOrderId())
                .razorpayPaymentId(payment.getRazorpayPaymentId())
                .productDetails(payment.getProductDetails())
                .status(payment.getStatus())
                .razorpayKeyId(razorpayConfig.getKeyId())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
