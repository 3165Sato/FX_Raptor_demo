package com.example.fxraptor.backoffice.service;

import com.example.fxraptor.domain.CoverOrder;
import com.example.fxraptor.repository.CoverOrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CoverOrderQueryService {

    private final CoverOrderRepository coverOrderRepository;

    public CoverOrderQueryService(CoverOrderRepository coverOrderRepository) {
        this.coverOrderRepository = coverOrderRepository;
    }

    public List<CoverOrder> findAll() {
        return coverOrderRepository.findAll();
    }
}
