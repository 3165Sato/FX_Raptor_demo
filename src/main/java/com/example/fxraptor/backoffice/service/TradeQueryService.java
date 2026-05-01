package com.example.fxraptor.backoffice.service;

import com.example.fxraptor.backoffice.dto.AdminTradeResponse;
import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.Trade;
import com.example.fxraptor.repository.AccountRepository;
import com.example.fxraptor.repository.TradeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

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

    public List<Trade> findAllByAccountId(Long accountId) {
        return resolveUserId(accountId)
                .map(tradeRepository::findAllByUserId)
                .orElseGet(tradeRepository::findAll);
    }

    public List<AdminTradeResponse> findAllAdminTrades(Long accountId) {
        return findAllByAccountId(accountId).stream()
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

    private Optional<String> resolveUserId(Long accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        return accountRepository.findById(accountId).map(Account::getUserId);
    }
}
