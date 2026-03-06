package com.example.fxraptor.risk.model;

import java.math.BigDecimal;

public record MarginCalculationResult(
        BigDecimal requiredMargin,
        BigDecimal effectiveMargin,
        BigDecimal marginMaintenanceRatio
) {
}
