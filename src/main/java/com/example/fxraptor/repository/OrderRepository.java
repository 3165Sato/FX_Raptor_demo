package com.example.fxraptor.repository;

import com.example.fxraptor.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
