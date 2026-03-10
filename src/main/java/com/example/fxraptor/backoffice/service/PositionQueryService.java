package com.example.fxraptor.backoffice.service;

import com.example.fxraptor.domain.Position;
import com.example.fxraptor.repository.PositionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PositionQueryService {

    private final PositionRepository positionRepository;

    public PositionQueryService(PositionRepository positionRepository) {
        this.positionRepository = positionRepository;
    }

    public List<Position> findAll() {
        return positionRepository.findAll();
    }
}
