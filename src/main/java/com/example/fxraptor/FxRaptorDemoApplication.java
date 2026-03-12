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
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.time.Instant;

@SpringBootApplication
public class FxRaptorDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(FxRaptorDemoApplication.class, args);
    }

    @Bean
    CommandLineRunner seedInitialData(AccountRepository accountRepository,
                                      MarginRuleRepository marginRuleRepository,
                                      OrderRepository orderRepository,
                                      TradeRepository tradeRepository,
                                      PositionRepository positionRepository) {
        return args -> {
            Account account = accountRepository.findByUserId("account-1").orElseGet(() -> {
                Account newAccount = new Account();
                newAccount.setUserId("account-1");
                newAccount.setBalance(new BigDecimal("1000000.0000"));
                newAccount.setCurrency("JPY");
                return accountRepository.save(newAccount);
            });

            marginRuleRepository.findById("USD/JPY").orElseGet(() -> {
                MarginRule marginRule = new MarginRule();
                marginRule.setCurrencyPair("USD/JPY");
                marginRule.setLeverage(new BigDecimal("25"));
                marginRule.setMaintenanceRate(new BigDecimal("100"));
                marginRule.setLiquidationRate(new BigDecimal("50"));
                return marginRuleRepository.save(marginRule);
            });

            Order order = orderRepository.findAll().stream().findFirst().orElseGet(() -> {
                Order newOrder = new Order();
                // Orderエンティティに accountId/sourceType がないため、現行定義の項目だけを初期投入する。
                newOrder.setUserId(account.getUserId());
                newOrder.setCurrencyPair("USD/JPY");
                newOrder.setSide(OrderSide.BUY);
                newOrder.setType(OrderType.MARKET);
                newOrder.setQuantity(new BigDecimal("10000"));
                newOrder.setStatus(OrderStatus.FILLED);
                return orderRepository.save(newOrder);
            });

            if (tradeRepository.count() == 0) {
                Trade trade = new Trade();
                trade.setOrderId(order.getId());
                trade.setUserId(account.getUserId());
                trade.setCurrencyPair("USD/JPY");
                trade.setSide(OrderSide.BUY);
                trade.setPrice(new BigDecimal("150.12"));
                trade.setQuantity(new BigDecimal("10000"));
                trade.setExecutedAt(Instant.now());
                tradeRepository.save(trade);
            }

            boolean hasPosition = positionRepository
                    .findByUserIdAndCurrencyPairAndSide(account.getUserId(), "USD/JPY", OrderSide.BUY)
                    .isPresent();
            if (!hasPosition) {
                Position position = new Position();
                position.setUserId(account.getUserId());
                position.setCurrencyPair("USD/JPY");
                position.setSide(OrderSide.BUY);
                position.setQuantity(new BigDecimal("10000"));
                position.setAvgPrice(new BigDecimal("150.120"));
                position.setUpdatedAt(Instant.now());
                positionRepository.save(position);
            }
        };
    }
}
