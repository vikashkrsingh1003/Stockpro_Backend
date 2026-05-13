package com.stockpro.payment.repository;

import com.stockpro.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findTopByPoIdOrderByCreatedAtDesc(Long poId);
    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);
    List<Payment> findByPoIdOrderByCreatedAtDesc(Long poId);
    List<Payment> findAllByOrderByCreatedAtDesc();
}
