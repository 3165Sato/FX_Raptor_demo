package com.example.fxraptor.backoffice.service;

import com.example.fxraptor.domain.Trade;
import com.example.fxraptor.repository.TradeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TradeQueryService {

    private final TradeRepository tradeRepository;

    public TradeQueryService(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    public List<Trade> findAll() {
        return tradeRepository.findAll();
    }
}
