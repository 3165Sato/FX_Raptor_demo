package com.example.fxraptor.backoffice.service;

import com.example.fxraptor.domain.LiquidationLog;
import com.example.fxraptor.repository.LiquidationLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LiquidationQueryService {

    private final LiquidationLogRepository liquidationLogRepository;

    public LiquidationQueryService(LiquidationLogRepository liquidationLogRepository) {
        this.liquidationLogRepository = liquidationLogRepository;
    }

    public List<LiquidationLog> findAll() {
        return liquidationLogRepository.findAll();
    }
}
