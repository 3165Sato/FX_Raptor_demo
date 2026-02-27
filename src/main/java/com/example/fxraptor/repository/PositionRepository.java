package com.example.fxraptor.repository;

import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.Position;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PositionRepository extends JpaRepository<Position, Long> {
    Optional<Position> findByUserIdAndCurrencyPairAndSide(String userId, String currencyPair, OrderSide side);
}
