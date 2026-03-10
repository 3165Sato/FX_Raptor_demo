package com.example.fxraptor.repository;

import com.example.fxraptor.domain.CoverExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoverExecutionLogRepository extends JpaRepository<CoverExecutionLog, Long> {
}
