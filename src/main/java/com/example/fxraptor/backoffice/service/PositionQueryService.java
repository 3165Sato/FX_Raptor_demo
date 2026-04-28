package com.example.fxraptor.backoffice.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.fxraptor.backoffice.dto.AdminPositionResponse;
import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.Position;
import com.example.fxraptor.repository.AccountRepository;
import com.example.fxraptor.repository.PositionRepository;

@Service
public class PositionQueryService {

    private static final Logger log = LoggerFactory.getLogger(PositionQueryService.class);

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

    public List<AdminPositionResponse> findAllAdminPositions(String accountId) {
        List<Position> positions;
        if (accountId == null) {
            positions = positionRepository.findAll();
            log.info("admin positions query count={}", positions.size());
        } else {
            positions = positionRepository.findAllByUserId(accountId);
            log.info("admin positions query count={}", positions.size());
        }

        return positions.stream()
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
