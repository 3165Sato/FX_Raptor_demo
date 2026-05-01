package com.example.fxraptor.backoffice.service;

import com.example.fxraptor.backoffice.dto.AdminOrderResponse;
import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.Order;
import com.example.fxraptor.repository.AccountRepository;
import com.example.fxraptor.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

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

    public List<Order> findAllByAccountId(Long accountId) {
        return resolveUserId(accountId)
                .map(orderRepository::findAllByUserId)
                .orElseGet(orderRepository::findAll);
    }

    public List<AdminOrderResponse> findAllAdminOrders(Long accountId) {
        return findAllByAccountId(accountId).stream()
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
        return order.getSourceType() == null ? null : order.getSourceType().name();
    }

    private Optional<String> resolveUserId(Long accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        return accountRepository.findById(accountId).map(Account::getUserId);
    }
}
