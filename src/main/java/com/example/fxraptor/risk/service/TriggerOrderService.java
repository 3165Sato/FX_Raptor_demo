package com.example.fxraptor.risk.service;

import com.example.fxraptor.domain.TriggerOrder;
import com.example.fxraptor.domain.TriggerStatus;
import com.example.fxraptor.repository.TriggerOrderRepository;
import org.springframework.stereotype.Service;

/**
 * 投資家チャネルからのTrigger登録を扱う最小サービス。
 */
@Service
public class TriggerOrderService {

    private final TriggerOrderRepository triggerOrderRepository;

    public TriggerOrderService(TriggerOrderRepository triggerOrderRepository) {
        this.triggerOrderRepository = triggerOrderRepository;
    }

    public TriggerOrder create(TriggerOrder triggerOrder) {
        if (triggerOrder.getStatus() == null) {
            triggerOrder.setStatus(TriggerStatus.ACTIVE);
        }
        return triggerOrderRepository.save(triggerOrder);
    }
}
