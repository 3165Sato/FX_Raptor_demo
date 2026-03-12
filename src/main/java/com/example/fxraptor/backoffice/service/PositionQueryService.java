package com.example.fxraptor.backoffice.service;

import com.example.fxraptor.backoffice.dto.AdminPositionResponse;
import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.Position;
import com.example.fxraptor.repository.AccountRepository;
import com.example.fxraptor.repository.PositionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PositionQueryService {

    private final PositionRepository positionRepository;
    private final AccountRepository accountRepository;

    public PositionQueryService(PositionRepository positionRepository,
                                AccountRepository accountRepository) {
        this.positionRepository = positionRepository;
        this.accountRepository = accountRepository;
    }

    public List<Position> findAll() {
        return positionRepository.findAll();
    }

    public List<AdminPositionResponse> findAllAdminPositions() {
        return positionRepository.findAll().stream()
                .map(this::toAdminPositionResponse)
                .toList();
    }

    private AdminPositionResponse toAdminPositionResponse(Position position) {
        Long accountId = accountRepository.findByUserId(position.getUserId())
                .map(Account::getId)
                .orElse(null);

        return new AdminPositionResponse(
                position.getId(),
                accountId,
                position.getCurrencyPair(),
                position.getSide(),
                position.getQuantity(),
                position.getAvgPrice(),
                position.getUpdatedAt()
        );
    }
}
