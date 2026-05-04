package com.example.fxraptor.order.service;

import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.Position;
import com.example.fxraptor.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public void applyRealizedPnl(String userId,
                                 Position closedPosition,
                                 BigDecimal executionPrice,
                                 BigDecimal closedQty) {
        if (closedPosition == null || closedQty.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Account account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("account not found for userId: " + userId));
        BigDecimal pnl = calculateRealizedPnl(closedPosition, executionPrice, closedQty);
        account.setBalance(account.getBalance().add(pnl));
        Account saved = accountRepository.save(account);
        Account accountToLog = saved == null ? account : saved;
        log.info("Applied realized PnL. accountId={}, userId={}, pnl={}, newBalance={}, currencyPair={}, closedPositionId={}",
                accountToLog.getId(), accountToLog.getUserId(), pnl, accountToLog.getBalance(),
                closedPosition.getCurrencyPair(), closedPosition.getId());
    }

    public BigDecimal calculateRealizedPnl(Position closedPosition,
                                           BigDecimal executionPrice,
                                           BigDecimal closedQty) {
        if (closedPosition.getSide() == OrderSide.BUY) {
            return executionPrice.subtract(closedPosition.getAvgPrice()).multiply(closedQty);
        }
        return closedPosition.getAvgPrice().subtract(executionPrice).multiply(closedQty);
    }
}
