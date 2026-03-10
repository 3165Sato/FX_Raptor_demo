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
 * カバー執行結果の監査ログ。
 */
@Entity
@Table(name = "cover_execution_logs")
public class CoverExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long coverExecutionLogId;

    @Column(nullable = false)
    private Long coverOrderId;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal executedPrice;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal executedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CoverExecutionResult executionResult;

    @Column(nullable = false, updatable = false)
    private Instant executedAt;

    @Column(length = 255)
    private String message;

    @PrePersist
    public void onCreate() {
        if (executedAt == null) {
            executedAt = Instant.now();
        }
    }

    public Long getCoverExecutionLogId() {
        return coverExecutionLogId;
    }

    public void setCoverExecutionLogId(Long coverExecutionLogId) {
        this.coverExecutionLogId = coverExecutionLogId;
    }

    public Long getCoverOrderId() {
        return coverOrderId;
    }

    public void setCoverOrderId(Long coverOrderId) {
        this.coverOrderId = coverOrderId;
    }

    public BigDecimal getExecutedPrice() {
        return executedPrice;
    }

    public void setExecutedPrice(BigDecimal executedPrice) {
        this.executedPrice = executedPrice;
    }

    public BigDecimal getExecutedQuantity() {
        return executedQuantity;
    }

    public void setExecutedQuantity(BigDecimal executedQuantity) {
        this.executedQuantity = executedQuantity;
    }

    public CoverExecutionResult getExecutionResult() {
        return executionResult;
    }

    public void setExecutionResult(CoverExecutionResult executionResult) {
        this.executionResult = executionResult;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(Instant executedAt) {
        this.executedAt = executedAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
