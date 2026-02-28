package com.example.fxraptor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "margin_rules")
public class MarginRule {

    @Id
    @Column(nullable = false, length = 20)
    private String currencyPair;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal leverage;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal maintenanceRate;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal liquidationRate;

    public String getCurrencyPair() {
        return currencyPair;
    }

    public void setCurrencyPair(String currencyPair) {
        this.currencyPair = currencyPair;
    }

    public BigDecimal getLeverage() {
        return leverage;
    }

    public void setLeverage(BigDecimal leverage) {
        this.leverage = leverage;
    }

    public BigDecimal getMaintenanceRate() {
        return maintenanceRate;
    }

    public void setMaintenanceRate(BigDecimal maintenanceRate) {
        this.maintenanceRate = maintenanceRate;
    }

    public BigDecimal getLiquidationRate() {
        return liquidationRate;
    }

    public void setLiquidationRate(BigDecimal liquidationRate) {
        this.liquidationRate = liquidationRate;
    }
}
