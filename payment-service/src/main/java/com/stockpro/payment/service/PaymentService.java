package com.stockpro.payment.service;

import com.stockpro.payment.dto.PaymentRequest;
import com.stockpro.payment.dto.PaymentReportRequest;
import com.stockpro.payment.dto.PaymentResponse;
import com.stockpro.payment.dto.PaymentVerificationRequest;

import java.util.List;

public interface PaymentService {
    PaymentResponse initiatePayment(PaymentRequest request);
    PaymentResponse generatePaymentReport(PaymentReportRequest request);
    PaymentResponse processPaymentReport(Long paymentId);
    List<PaymentResponse> getPaymentReports();
    PaymentResponse verifyPayment(PaymentVerificationRequest request);
    PaymentResponse getLatestPaymentByPoId(Long poId);
}
