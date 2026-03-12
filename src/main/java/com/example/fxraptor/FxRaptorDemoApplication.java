package com.example.fxraptor;

import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.Order;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.OrderStatus;
import com.example.fxraptor.domain.OrderType;
import com.example.fxraptor.domain.Trade;
import com.example.fxraptor.repository.AccountRepository;
import com.example.fxraptor.repository.OrderRepository;
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
                                      OrderRepository orderRepository,
                                      TradeRepository tradeRepository) {
        return args -> {
            Account account = accountRepository.findByUserId("account-1").orElse(null);
            if (account == null) {
                account = new Account();
                account.setUserId("account-1");
                account.setBalance(new BigDecimal("1000000.0000"));
                account.setCurrency("JPY");
                account = accountRepository.save(account);
            }

            Order order = orderRepository.findAll().stream().findFirst().orElse(null);
            if (order == null) {
                order = new Order();
                // Orderエンティティに accountId/sourceType がないため、現行定義の項目だけを初期投入する。
                order.setUserId(account.getUserId());
                order.setCurrencyPair("USD/JPY");
                order.setSide(OrderSide.BUY);
                order.setType(OrderType.MARKET);
                order.setQuantity(new BigDecimal("10000"));
                order.setStatus(OrderStatus.FILLED);
                order = orderRepository.save(order);
            }

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
            tradeRepository.save(trade);
        };
    }
}
