package com.example.fxraptor.backoffice.service;

import com.example.fxraptor.domain.TriggerOrder;
import com.example.fxraptor.repository.TriggerOrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TriggerOrderQueryService {

    private final TriggerOrderRepository triggerOrderRepository;

    public TriggerOrderQueryService(TriggerOrderRepository triggerOrderRepository) {
        this.triggerOrderRepository = triggerOrderRepository;
    }

    public List<TriggerOrder> findAll() {
        return triggerOrderRepository.findAll();
    }
}
