package com.example.fxraptor.order.service;

import com.example.fxraptor.domain.CoverExecutionLog;
import com.example.fxraptor.domain.CoverExecutionResult;
import com.example.fxraptor.domain.CoverOrder;
import com.example.fxraptor.domain.CoverOrderStatus;
import com.example.fxraptor.order.model.CoverOrderCommand;
import com.example.fxraptor.repository.CoverExecutionLogRepository;
import com.example.fxraptor.repository.CoverOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * カバー注文の疑似執行。外部LPには接続せず、即時約定として記録する。
 */
@Service
public class CoverExecutionService {

    private static final Logger log = LoggerFactory.getLogger(CoverExecutionService.class);

    private final CoverOrderRepository coverOrderRepository;
    private final CoverExecutionLogRepository coverExecutionLogRepository;

    public CoverExecutionService(CoverOrderRepository coverOrderRepository,
                                 CoverExecutionLogRepository coverExecutionLogRepository) {
        this.coverOrderRepository = coverOrderRepository;
        this.coverExecutionLogRepository = coverExecutionLogRepository;
    }

    public CoverOrder execute(CoverOrderCommand command) {
        log.info("Executing cover order. tradeId={}, accountId={}, currencyPair={}, side={}, quantity={}",
                command.tradeId(), command.accountId(), command.currencyPair(), command.side(), command.quantity());
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

        CoverExecutionLog executionLog = new CoverExecutionLog();
        executionLog.setCoverOrderId(saved.getCoverOrderId());
        executionLog.setExecutedPrice(command.requestedPrice());
        executionLog.setExecutedQuantity(command.quantity());
        executionLog.setExecutionResult(CoverExecutionResult.SUCCESS);
        executionLog.setExecutedAt(Instant.now());
        executionLog.setMessage("simulated cover fill");
        coverExecutionLogRepository.save(executionLog);

        log.info("Cover order completed. coverOrderId={}, tradeId={}, accountId={}",
                saved.getCoverOrderId(), saved.getTradeId(), saved.getAccountId());

        return saved;
    }
}
