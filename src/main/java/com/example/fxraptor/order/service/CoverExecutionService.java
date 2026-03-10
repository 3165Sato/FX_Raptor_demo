package com.example.fxraptor.order.service;

import com.example.fxraptor.domain.CoverExecutionLog;
import com.example.fxraptor.domain.CoverExecutionResult;
import com.example.fxraptor.domain.CoverOrder;
import com.example.fxraptor.domain.CoverOrderStatus;
import com.example.fxraptor.order.model.CoverOrderCommand;
import com.example.fxraptor.repository.CoverExecutionLogRepository;
import com.example.fxraptor.repository.CoverOrderRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * カバー注文の疑似執行。外部LPには接続せず、即時約定として記録する。
 */
@Service
public class CoverExecutionService {

    private final CoverOrderRepository coverOrderRepository;
    private final CoverExecutionLogRepository coverExecutionLogRepository;

    public CoverExecutionService(CoverOrderRepository coverOrderRepository,
                                 CoverExecutionLogRepository coverExecutionLogRepository) {
        this.coverOrderRepository = coverOrderRepository;
        this.coverExecutionLogRepository = coverExecutionLogRepository;
    }

    public CoverOrder execute(CoverOrderCommand command) {
        CoverOrder order = new CoverOrder();
        order.setTradeId(command.tradeId());
        order.setAccountId(command.accountId());
        order.setCurrencyPair(command.currencyPair());
        order.setSide(command.side());
        order.setQuantity(command.quantity());
        order.setRequestedPrice(command.requestedPrice());
        order.setCoverMode(command.coverMode());
        order.setStatus(CoverOrderStatus.FILLED);
        CoverOrder saved = coverOrderRepository.save(order);

        CoverExecutionLog log = new CoverExecutionLog();
        log.setCoverOrderId(saved.getCoverOrderId());
        log.setExecutedPrice(command.requestedPrice());
        log.setExecutedQuantity(command.quantity());
        log.setExecutionResult(CoverExecutionResult.SUCCESS);
        log.setExecutedAt(Instant.now());
        log.setMessage("simulated cover fill");
        coverExecutionLogRepository.save(log);

        return saved;
    }
}
