package com.example.fxraptor.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.domain.Position;

public interface PositionRepository extends JpaRepository<Position, Long> {
    /**
     * ネットポジション方式の主キー代わりとなる検索。
     * userId, currencyPair, side の組み合わせで 1 件だけ存在する前提。
     */
    Optional<Position> findByUserIdAndCurrencyPairAndSide(String userId, String currencyPair, OrderSide side);

    List<Position> findAllByUserId(String userId);

    // List<Position> findAllByUserId(Long accountId);
}
