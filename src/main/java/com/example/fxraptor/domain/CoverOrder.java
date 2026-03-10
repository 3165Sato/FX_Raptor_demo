package com.example.fxraptor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 顧客約定に対して事業者側で発行するカバー注文。
 */
@Entity
@Table(name = "cover_orders")
public class CoverOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long coverOrderId;

    @Column(nullable = false)
    private Long tradeId;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false, length = 20)
    private String currencyPair;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrderSide side;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal requestedPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CoverOrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CoverMode coverMode;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getCoverOrderId() {
        return coverOrderId;
    }

    public void setCoverOrderId(Long coverOrderId) {
        this.coverOrderId = coverOrderId;
    }

    public Long getTradeId() {
        return tradeId;
    }

    public void setTradeId(Long tradeId) {
        this.tradeId = tradeId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
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

    public BigDecimal getRequestedPrice() {
        return requestedPrice;
    }

    public void setRequestedPrice(BigDecimal requestedPrice) {
        this.requestedPrice = requestedPrice;
    }

    public CoverOrderStatus getStatus() {
        return status;
    }

    public void setStatus(CoverOrderStatus status) {
        this.status = status;
    }

    public CoverMode getCoverMode() {
        return coverMode;
    }

    public void setCoverMode(CoverMode coverMode) {
        this.coverMode = coverMode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
