package com.stockpro.analytics.repository;

import com.stockpro.analytics.entity.ProductPerformance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductPerformanceRepository extends JpaRepository<ProductPerformance, Long> {

    List<ProductPerformance> findByMovementCategory(String category);

    List<ProductPerformance> findTop10ByOrderByTurnoverRateDesc();
}
