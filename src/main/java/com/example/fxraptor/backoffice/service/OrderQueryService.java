package com.example.fxraptor.backoffice.service;

import com.example.fxraptor.backoffice.dto.AdminOrderResponse;
import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.Order;
import com.example.fxraptor.repository.AccountRepository;
import com.example.fxraptor.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;

    public OrderQueryService(OrderRepository orderRepository,
                             AccountRepository accountRepository) {
        this.orderRepository = orderRepository;
        this.accountRepository = accountRepository;
    }

    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    public List<AdminOrderResponse> findAllAdminOrders() {
        return orderRepository.findAll().stream()
                .map(this::toAdminOrderResponse)
                .toList();
    }

    private AdminOrderResponse toAdminOrderResponse(Order order) {
        Long accountId = accountRepository.findByUserId(order.getUserId())
                .map(Account::getId)
                .orElse(null);

        return new AdminOrderResponse(
                order.getId(),
                accountId,
                order.getCurrencyPair(),
                order.getSide(),
                order.getType(),
                order.getQuantity(),
                order.getStatus(),
                resolveSourceType(order),
                order.getCreatedAt()
        );
    }

    private String resolveSourceType(Order order) {
        // 現行のOrderエンティティは発生元を保持していないため、管理画面では既定値として USER を返す。
        return "USER";
    }
}
