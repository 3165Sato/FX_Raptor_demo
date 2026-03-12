package com.example.fxraptor.backoffice.service;

import com.example.fxraptor.backoffice.dto.AdminTradeResponse;
import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.Trade;
import com.example.fxraptor.repository.AccountRepository;
import com.example.fxraptor.repository.TradeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TradeQueryService {

    private final TradeRepository tradeRepository;
    private final AccountRepository accountRepository;

    public TradeQueryService(TradeRepository tradeRepository,
                             AccountRepository accountRepository) {
        this.tradeRepository = tradeRepository;
        this.accountRepository = accountRepository;
    }

    public List<Trade> findAll() {
        return tradeRepository.findAll();
    }

    public List<AdminTradeResponse> findAllAdminTrades() {
        return tradeRepository.findAll().stream()
                .map(this::toAdminTradeResponse)
                .toList();
    }

    private AdminTradeResponse toAdminTradeResponse(Trade trade) {
        Long accountId = accountRepository.findByUserId(trade.getUserId())
                .map(Account::getId)
                .orElse(null);

        return new AdminTradeResponse(
                trade.getId(),
                trade.getOrderId(),
                accountId,
                trade.getCurrencyPair(),
                trade.getSide(),
                trade.getPrice(),
                trade.getQuantity(),
                trade.getExecutedAt()
        );
    }
}
