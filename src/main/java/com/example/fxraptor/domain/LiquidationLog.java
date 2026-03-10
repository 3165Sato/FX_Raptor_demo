package com.example.fxraptor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * ロスカット実行履歴の参照用ログ。
 */
@Entity
@Table(name = "liquidation_logs")
public class LiquidationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long liquidationLogId;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private Long orderId;

    @Column
    private Long tradeId;

    @Column(nullable = false, length = 20)
    private String currencyPair;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrderSide side;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    @Column(nullable = false, length = 255)
    private String liquidationReason;

    @Column(precision = 19, scale = 4)
    private BigDecimal marginRatioAtLiquidation;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getLiquidationLogId() {
        return liquidationLogId;
    }

    public void setLiquidationLogId(Long liquidationLogId) {
        this.liquidationLogId = liquidationLogId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getTradeId() {
        return tradeId;
    }

    public void setTradeId(Long tradeId) {
        this.tradeId = tradeId;
    }

    public String getCurrencyPair() {
        return currencyPair;
    }

    public void setCurrencyPair(String currencyPair) {
        this.currencyPair = currencyPair;
    }

    public OrderSide getSide() {
        return side;
    }

    public void setSide(OrderSide side) {
        this.side = side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getLiquidationReason() {
        return liquidationReason;
    }

    public void setLiquidationReason(String liquidationReason) {
        this.liquidationReason = liquidationReason;
    }

    public BigDecimal getMarginRatioAtLiquidation() {
        return marginRatioAtLiquidation;
    }

    public void setMarginRatioAtLiquidation(BigDecimal marginRatioAtLiquidation) {
        this.marginRatioAtLiquidation = marginRatioAtLiquidation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
