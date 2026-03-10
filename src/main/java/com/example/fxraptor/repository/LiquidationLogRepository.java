package com.example.fxraptor.repository;

import com.example.fxraptor.domain.LiquidationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiquidationLogRepository extends JpaRepository<LiquidationLog, Long> {
}
