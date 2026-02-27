package com.example.fxraptor.service.dto;

import java.math.BigDecimal;

public record MarginCalculationResult(
        BigDecimal requiredMargin,
        BigDecimal effectiveMargin,
        BigDecimal marginMaintenanceRatio
) {
}
