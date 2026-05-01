package com.example.fxraptor;

import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.MarginRule;
import com.example.fxraptor.domain.Order;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.OrderStatus;
import com.example.fxraptor.domain.OrderType;
import com.example.fxraptor.domain.Position;
import com.example.fxraptor.domain.Trade;
import com.example.fxraptor.repository.AccountRepository;
import com.example.fxraptor.repository.MarginRuleRepository;
import com.example.fxraptor.repository.OrderRepository;
import com.example.fxraptor.repository.PositionRepository;
import com.example.fxraptor.repository.TradeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.time.Instant;

@SpringBootApplication
public class FxRaptorDemoApplication {

    private static final Logger log = LoggerFactory.getLogger(FxRaptorDemoApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(FxRaptorDemoApplication.class, args);
    }

    @Bean
    @ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
    CommandLineRunner seedInitialData(AccountRepository accountRepository,
                                      MarginRuleRepository marginRuleRepository,
                                      OrderRepository orderRepository,
                                      TradeRepository tradeRepository,
                                      PositionRepository positionRepository) {
        return args -> {
            Account account = seedAccount(accountRepository);
            seedMarginRule(marginRuleRepository);
            Order order = seedOrder(orderRepository, account);
            seedTrade(tradeRepository, account, order);
            seedPosition(positionRepository, account);

            log.info("initial data ready: accounts={}, orders={}, trades={}, positions={}",
                    accountRepository.count(),
                    orderRepository.count(),
                    tradeRepository.count(),
                    positionRepository.count());
        };
    }

    private Account seedAccount(AccountRepository accountRepository) {
        return accountRepository.findByUserId("account-1").orElseGet(() -> {
            Account account = new Account();
            account.setUserId("account-1");
            account.setBalance(new BigDecimal("1000000.0000"));
            account.setCurrency("JPY");
            Account saved = accountRepository.save(account);
            log.info("seeded account: userId={}", saved.getUserId());
            return saved;
        });
    }

    private void seedMarginRule(MarginRuleRepository marginRuleRepository) {
        marginRuleRepository.findById("USD/JPY").orElseGet(() -> {
            MarginRule marginRule = new MarginRule();
            marginRule.setCurrencyPair("USD/JPY");
            marginRule.setLeverage(new BigDecimal("25"));
            marginRule.setMaintenanceRate(new BigDecimal("100"));
            marginRule.setLiquidationRate(new BigDecimal("50"));
            MarginRule saved = marginRuleRepository.save(marginRule);
            log.info("seeded margin rule: currencyPair={}", saved.getCurrencyPair());
            return saved;
        });
    }

    private Order seedOrder(OrderRepository orderRepository, Account account) {
        return orderRepository.findAll().stream().findFirst().orElseGet(() -> {
            Order order = new Order();
            order.setUserId(account.getUserId());
            order.setCurrencyPair("USD/JPY");
            order.setSide(OrderSide.BUY);
            order.setType(OrderType.MARKET);
            order.setQuantity(new BigDecimal("10000"));
            order.setStatus(OrderStatus.FILLED);
            Order saved = orderRepository.save(order);
            log.info("seeded order: orderId={}", saved.getId());
            return saved;
        });
    }

    private void seedTrade(TradeRepository tradeRepository, Account account, Order order) {
        if (tradeRepository.count() > 0) {
            return;
        }

        Trade trade = new Trade();
        trade.setOrderId(order.getId());
        trade.setUserId(account.getUserId());
        trade.setCurrencyPair("USD/JPY");
        trade.setSide(OrderSide.BUY);
        trade.setPrice(new BigDecimal("150.12"));
        trade.setQuantity(new BigDecimal("10000"));
        trade.setExecutedAt(Instant.now());
        Trade saved = tradeRepository.save(trade);
        log.info("seeded trade: tradeId={}", saved.getId());
    }

    private void seedPosition(PositionRepository positionRepository, Account account) {
        boolean exists = positionRepository
                .findByUserIdAndCurrencyPairAndSide(account.getUserId(), "USD/JPY", OrderSide.BUY)
                .isPresent();
        if (exists) {
            log.info("position seed skipped: userId=account-1 currencyPair=USD/JPY side=BUY");
            return;
        }

        Position position = new Position();
        position.setUserId(account.getUserId());
        position.setCurrencyPair("USD/JPY");
        position.setSide(OrderSide.BUY);
        position.setQuantity(new BigDecimal("10000"));
        position.setAvgPrice(new BigDecimal("150.120"));
        position.setUpdatedAt(Instant.now());
        Position saved = positionRepository.save(position);
        log.info("seeded position: positionId={}", saved.getId());
    }
}
