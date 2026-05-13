package com.stockpro.payment.service.impl;

import com.razorpay.Order;
import com.razorpay.OrderClient;
import com.razorpay.RazorpayClient;
import com.stockpro.payment.client.PurchaseServiceClient;
import com.stockpro.payment.config.RazorpayConfig;
import com.stockpro.payment.dto.PaymentReportRequest;
import com.stockpro.payment.dto.PaymentRequest;
import com.stockpro.payment.dto.PaymentVerificationRequest;
import com.stockpro.payment.entity.Payment;
import com.stockpro.payment.entity.PaymentStatus;
import com.stockpro.payment.exception.InvalidPaymentSignatureException;
import com.stockpro.payment.exception.PaymentException;
import com.stockpro.payment.exception.PaymentNotFoundException;
import com.stockpro.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private RazorpayClient razorpayClient;

    @Mock
    private OrderClient orderClient;

    @Mock
    private RazorpayConfig razorpayConfig;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PurchaseServiceClient purchaseServiceClient;

    @InjectMocks
    private PaymentServiceImpl service;

    private Payment payment;

    @BeforeEach
    void setup() {
        razorpayClient.orders = orderClient;
        payment = Payment.builder()
                .paymentId(1L)
                .poId(2L)
                .supplierId(3L)
                .amount(new BigDecimal("1500.00"))
                .currency("INR")
                .razorpayOrderId("order_123")
                .productDetails("Phone | Qty: 1")
                .status(PaymentStatus.PENDING)
                .build();
    }

    @Test
    void initiatePayment_success() throws Exception {
        PaymentRequest request = new PaymentRequest();
        request.setPoId(2L);
        request.setSupplierId(3L);
        request.setAmount(new BigDecimal("1500.00"));
        request.setCurrency("inr");
        Order order = mock(Order.class);
        when(order.get("id")).thenReturn("order_123");
        when(orderClient.create(any())).thenReturn(order);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment saved = invocation.getArgument(0);
            saved.setPaymentId(1L);
            return saved;
        });
        when(razorpayConfig.getKeyId()).thenReturn("rzp_test");

        var response = service.initiatePayment(request);

        assertEquals(1L, response.getPaymentId());
        assertEquals("INR", response.getCurrency());
        assertEquals("order_123", response.getRazorpayOrderId());
    }

    @Test
    void initiatePayment_razorpayAmountTooHigh() {
        PaymentRequest request = new PaymentRequest();
        request.setPoId(2L);
        request.setSupplierId(3L);
        request.setAmount(new BigDecimal("1050000.00"));
        request.setCurrency("INR");

        assertThrows(PaymentException.class, () -> service.initiatePayment(request));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void generatePaymentReport_createsNewReportEvenWhenPoAlreadyHasPendingReport() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment saved = invocation.getArgument(0);
            saved.setPaymentId(9L);
            return saved;
        });
        when(razorpayConfig.getKeyId()).thenReturn("rzp_test");

        var response = service.generatePaymentReport(reportRequest());

        assertEquals(9L, response.getPaymentId());
        assertEquals(PaymentStatus.PENDING, response.getStatus());
        assertTrue(response.getRazorpayOrderId().startsWith("REPORT-2-"));
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void generatePaymentReport_createsNewReport() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment saved = invocation.getArgument(0);
            saved.setPaymentId(9L);
            return saved;
        });
        when(razorpayConfig.getKeyId()).thenReturn("rzp_test");

        var response = service.generatePaymentReport(reportRequest());

        assertEquals(9L, response.getPaymentId());
        assertEquals(PaymentStatus.PENDING, response.getStatus());
        assertTrue(response.getRazorpayOrderId().startsWith("REPORT-2-"));
    }

    @Test
    void processPaymentReport_successCreatesRazorpayOrder() throws Exception {
        Order order = mock(Order.class);
        when(order.get("id")).thenReturn("order_new");
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(orderClient.create(any())).thenReturn(order);
        when(paymentRepository.save(payment)).thenReturn(payment);
        when(razorpayConfig.getKeyId()).thenReturn("rzp_test");

        var response = service.processPaymentReport(1L);

        assertEquals("order_new", response.getRazorpayOrderId());
        assertEquals(PaymentStatus.PENDING, response.getStatus());
    }

    @Test
    void processPaymentReport_returnsSuccessWithoutNewOrder() {
        payment.setStatus(PaymentStatus.SUCCESS);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(razorpayConfig.getKeyId()).thenReturn("rzp_test");

        var response = service.processPaymentReport(1L);

        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        verifyNoInteractions(razorpayClient);
    }

    @Test
    void processPaymentReport_notFound() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class, () -> service.processPaymentReport(1L));
    }

    @Test
    void processPaymentReport_rejectsAmountAboveRazorpayLimit() {
        payment.setAmount(new BigDecimal("1050000.00"));
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        assertThrows(PaymentException.class, () -> service.processPaymentReport(1L));
        verifyNoInteractions(razorpayClient);
    }

    @Test
    void getPaymentReports_success() {
        when(paymentRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(payment));
        when(razorpayConfig.getKeyId()).thenReturn("rzp_test");

        assertEquals(1, service.getPaymentReports().size());
    }

    @Test
    void verifyPayment_notFound() {
        PaymentVerificationRequest request = verificationRequest();
        when(paymentRepository.findByRazorpayOrderId("order_123")).thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class, () -> service.verifyPayment(request));
    }

    @Test
    void verifyPayment_invalidSignatureMarksFailedAndThrows() {
        PaymentVerificationRequest request = verificationRequest();
        when(paymentRepository.findByRazorpayOrderId("order_123")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);

        assertThrows(InvalidPaymentSignatureException.class, () -> service.verifyPayment(request));

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        verify(purchaseServiceClient).updatePaymentStatus(2L, "PAYMENT_FAILED", "order_123", "pay_123");
    }

    @Test
    void verifyPayment_validSignatureMarksSuccessAndUpdatesPurchaseOrder() throws Exception {
        PaymentVerificationRequest request = verificationRequest();
        request.setRazorpaySignature(hmacSha256("order_123|pay_123", "test_secret"));
        when(paymentRepository.findByRazorpayOrderId("order_123")).thenReturn(Optional.of(payment));
        when(razorpayConfig.getKeySecret()).thenReturn("test_secret");
        when(paymentRepository.save(payment)).thenReturn(payment);
        when(razorpayConfig.getKeyId()).thenReturn("rzp_test");

        var response = service.verifyPayment(request);

        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        assertEquals("pay_123", response.getRazorpayPaymentId());
        verify(purchaseServiceClient).updatePaymentStatus(2L, "PAID", "order_123", "pay_123");
    }

    @Test
    void getLatestPaymentByPoId_success() {
        when(paymentRepository.findTopByPoIdOrderByCreatedAtDesc(2L)).thenReturn(Optional.of(payment));
        when(razorpayConfig.getKeyId()).thenReturn("rzp_test");

        assertEquals(1L, service.getLatestPaymentByPoId(2L).getPaymentId());
    }

    @Test
    void getLatestPaymentByPoId_notFound() {
        when(paymentRepository.findTopByPoIdOrderByCreatedAtDesc(2L)).thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class, () -> service.getLatestPaymentByPoId(2L));
    }

    private PaymentReportRequest reportRequest() {
        PaymentReportRequest request = new PaymentReportRequest();
        request.setPoId(2L);
        request.setSupplierId(3L);
        request.setAmount(new BigDecimal("1500.00"));
        request.setCurrency("inr");
        request.setProductDetails("Phone | Qty: 1");
        return request;
    }

    private PaymentVerificationRequest verificationRequest() {
        PaymentVerificationRequest request = new PaymentVerificationRequest();
        request.setRazorpayOrderId("order_123");
        request.setRazorpayPaymentId("pay_123");
        request.setRazorpaySignature("bad_signature");
        return request;
    }

    private String hmacSha256(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder signature = new StringBuilder();
        for (byte b : digest) {
            signature.append(String.format("%02x", b));
        }
        return signature.toString();
    }
}
