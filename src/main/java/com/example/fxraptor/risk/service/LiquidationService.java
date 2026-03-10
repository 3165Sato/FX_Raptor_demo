package com.example.fxraptor.risk.service;

import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.MarginRule;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.Position;
import com.example.fxraptor.domain.Quote;
import com.example.fxraptor.infra.event.LiquidationRequestedEvent;
import com.example.fxraptor.infra.event.OneSecondAggregatedEvent;
import com.example.fxraptor.repository.AccountRepository;
import com.example.fxraptor.repository.MarginRuleRepository;
import com.example.fxraptor.repository.PositionRepository;
import com.example.fxraptor.risk.model.MarginCalculationResult;
import com.example.fxraptor.risk.model.InternalOrderCommand;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ロスカット対象ポジションから、内部成行コマンドを生成するサービス。
 */
@Service
public class LiquidationService {

    private static final String LIQUIDATION_REASON = "MARGIN_RATIO_BELOW_LIQUIDATION_RATE";

    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final MarginRuleRepository marginRuleRepository;
    private final MarginService marginService;
    private final ApplicationEventPublisher eventPublisher;

    public LiquidationService(AccountRepository accountRepository,
                              PositionRepository positionRepository,
                              MarginRuleRepository marginRuleRepository,
                              MarginService marginService,
                              ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.marginRuleRepository = marginRuleRepository;
        this.marginService = marginService;
        this.eventPublisher = eventPublisher;
    }

    public List<InternalOrderCommand> createInternalCommands(Account account,
                                                             List<Position> positions,
                                                             Quote quote) {
        return positions.stream()
                .sorted(Comparator.comparing((Position position) -> positionNotional(position, quote)).reversed())
                .map(position -> new InternalOrderCommand(
                        account.getId(),
                        account.getUserId(),
                        position.getCurrencyPair(),
                        oppositeSide(position.getSide()),
                        position.getQuantity()
                ))
                .collect(Collectors.toList());
    }

    @EventListener
    public void onOneSecondAggregated(OneSecondAggregatedEvent event) {
        MarginRule rule = marginRuleRepository.findById(event.currencyPair()).orElse(null);
        if (rule == null) {
            return;
        }

        Quote quote = new Quote();
        quote.setCurrencyPair(event.currencyPair());
        quote.setBid(event.closeBid());
        quote.setAsk(event.closeAsk());
        quote.setTimestamp(event.windowEnd());

        for (Account account : accountRepository.findAll()) {
            List<Position> positions = positionRepository.findAllByUserId(account.getUserId()).stream()
                    .filter(position -> event.currencyPair().equals(position.getCurrencyPair()))
                    .toList();
            if (positions.isEmpty()) {
                continue;
            }
            MarginCalculationResult result = marginService.calculateResult(account, positions, List.of(quote), List.of(rule));
            if (!marginService.shouldLiquidate(positions, List.of(rule), result)) {
                continue;
            }

            List<InternalOrderCommand> commands = createInternalCommands(account, positions, quote);
            for (InternalOrderCommand command : commands) {
                eventPublisher.publishEvent(new LiquidationRequestedEvent(
                        command.accountId(),
                        command.currencyPair(),
                        command.side(),
                        command.quantity(),
                        LIQUIDATION_REASON,
                        Instant.now()
                ));
            }
        }
    }

    private BigDecimal positionNotional(Position position, Quote quote) {
        BigDecimal mark = position.getSide() == OrderSide.BUY ? quote.getBid() : quote.getAsk();
        return mark.multiply(position.getQuantity());
    }

    private OrderSide oppositeSide(OrderSide side) {
        return side == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY;
    }
}
