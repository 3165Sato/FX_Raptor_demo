package com.example.fxraptor.repository;

import com.example.fxraptor.domain.Trade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, Long> {
    List<Trade> findAllByUserId(String userId);
}
