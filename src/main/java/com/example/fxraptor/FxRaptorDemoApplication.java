package com.example.fxraptor;

import com.example.fxraptor.domain.Order;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.OrderStatus;
import com.example.fxraptor.domain.OrderType;
import com.example.fxraptor.repository.OrderRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;

@SpringBootApplication
public class FxRaptorDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(FxRaptorDemoApplication.class, args);
    }

    @Bean
    CommandLineRunner seedInitialOrder(OrderRepository orderRepository) {
        return args -> {
            if (orderRepository.count() > 0) {
                return;
            }

            Order order = new Order();
            // Orderエンティティに accountId/sourceType がないため、現行定義の項目だけを初期投入する。
            order.setUserId("account-1");
            order.setCurrencyPair("USD/JPY");
            order.setSide(OrderSide.BUY);
            order.setType(OrderType.MARKET);
            order.setQuantity(new BigDecimal("10000"));
            order.setStatus(OrderStatus.FILLED);
            orderRepository.save(order);
        };
    }
}
