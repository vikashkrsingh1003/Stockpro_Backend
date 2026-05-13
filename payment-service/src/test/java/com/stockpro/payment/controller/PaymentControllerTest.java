package com.stockpro.payment.controller;

import com.stockpro.payment.dto.PaymentReportRequest;
import com.stockpro.payment.dto.PaymentRequest;
import com.stockpro.payment.dto.PaymentResponse;
import com.stockpro.payment.dto.PaymentVerificationRequest;
import com.stockpro.payment.entity.PaymentStatus;
import com.stockpro.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    private PaymentController controller;
    private PaymentResponse response;

    @BeforeEach
    void setup() {
        controller = new PaymentController(paymentService);
        ReflectionTestUtils.setField(controller, "internalServiceToken", "internal");
        response = PaymentResponse.builder()
                .paymentId(1L)
                .poId(2L)
                .amount(BigDecimal.TEN)
                .status(PaymentStatus.PENDING)
                .build();
    }

    @Test
    void endpointsDelegateToService() {
        PaymentRequest request = new PaymentRequest();
        PaymentVerificationRequest verification = new PaymentVerificationRequest();
        when(paymentService.initiatePayment(request)).thenReturn(response);
        when(paymentService.getPaymentReports()).thenReturn(List.of(response));
        when(paymentService.processPaymentReport(1L)).thenReturn(response);
        when(paymentService.verifyPayment(verification)).thenReturn(response);
        when(paymentService.getLatestPaymentByPoId(2L)).thenReturn(response);

        assertSame(response, controller.initiatePayment(request).getBody());
        assertEquals(1, controller.getPaymentReports().getBody().size());
        assertSame(response, controller.processPaymentReport(1L).getBody());
        assertSame(response, controller.verifyPayment(verification).getBody());
        assertSame(response, controller.getPaymentStatus(2L).getBody());
    }

    @Test
    void generatePaymentReportRequiresInternalToken() {
        PaymentReportRequest request = new PaymentReportRequest();
        when(paymentService.generatePaymentReport(request)).thenReturn(response);

        assertEquals(200, controller.generatePaymentReport("internal", request).getStatusCode().value());
        assertEquals(401, controller.generatePaymentReport("wrong", request).getStatusCode().value());
    }
}
