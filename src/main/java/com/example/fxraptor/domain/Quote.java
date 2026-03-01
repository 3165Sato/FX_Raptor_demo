package com.example.fxraptor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * 通貨ペアの現在レート。
 * FX では Bid/Ask が異なるため、約定価格と評価損益で side に応じて使い分ける。
 */
@Entity
@Table(name = "quotes")
public class Quote {

    @Id
    @Column(nullable = false, length = 20)
    private String currencyPair;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal bid;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal ask;

    public String getCurrencyPair() {
        return currencyPair;
    }

    public void setCurrencyPair(String currencyPair) {
        this.currencyPair = currencyPair;
    }

    public BigDecimal getBid() {
        return bid;
    }

    public void setBid(BigDecimal bid) {
        this.bid = bid;
    }

    public BigDecimal getAsk() {
        return ask;
    }

    public void setAsk(BigDecimal ask) {
        this.ask = ask;
    }
}
