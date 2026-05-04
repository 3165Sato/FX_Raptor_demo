package com.example.fxraptor.order.service;

import com.example.fxraptor.domain.Position;
import com.example.fxraptor.domain.OrderSide;
import com.example.fxraptor.order.model.NettingResult;
import com.example.fxraptor.repository.PositionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PositionService {

    private static final int PRICE_SCALE = 8;
    private static final Logger log = LoggerFactory.getLogger(PositionService.class);
    private final PositionRepository positionRepository;

    public PositionService(PositionRepository positionRepository) {
        this.positionRepository = positionRepository;
    }

    public Position applyNettingResult(NettingResult nettingResult) {
        Position sameSide = nettingResult.sameSidePosition();
        Position oppositeSide = nettingResult.oppositeSidePosition();
        BigDecimal closedQuantity = nettingResult.closedQuantity();
        BigDecimal remainingQuantity = nettingResult.remainingQuantity();
        BigDecimal executionPrice = nettingResult.executionPrice();

        Position updatedPosition = sameSide;
        if (oppositeSide != null && closedQuantity.compareTo(BigDecimal.ZERO) > 0) {
            updatedPosition = reduceOrDeleteOppositePosition(oppositeSide, closedQuantity);
        }

        if (remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return updatedPosition;
        }

        if (sameSide == null) {
            return createNewPosition(nettingResult.trade().getUserId(),
                    nettingResult.trade().getCurrencyPair(),
                    nettingResult.trade().getSide(),
                    remainingQuantity,
                    executionPrice);
        }
        return updateSameSidePosition(sameSide, remainingQuantity, executionPrice);
    }

    public Position createNewPosition(String userId,
                                      String currencyPair,
                                      OrderSide side,
                                      BigDecimal quantity,
                                      BigDecimal executionPrice) {
        Position position = new Position();
        position.setUserId(userId);
        position.setCurrencyPair(currencyPair);
        position.setSide(side);
        position.setQuantity(quantity);
        position.setAvgPrice(executionPrice);
        Position saved = positionRepository.saveAndFlush(position);
        log.info("Created position. positionId={}, userId={}, currencyPair={}, side={}, quantity={}, avgPrice={}",
                saved.getId(), saved.getUserId(), saved.getCurrencyPair(), saved.getSide(), saved.getQuantity(), saved.getAvgPrice());
        return saved;
    }

    public Position updateSameSidePosition(Position sameSidePosition,
                                           BigDecimal remainingQuantity,
                                           BigDecimal executionPrice) {
        BigDecimal newQuantity = sameSidePosition.getQuantity().add(remainingQuantity);
        BigDecimal newAvgPrice = weightedAverage(
                sameSidePosition.getAvgPrice(),
                sameSidePosition.getQuantity(),
                executionPrice,
                remainingQuantity
        );
        sameSidePosition.setQuantity(newQuantity);
        sameSidePosition.setAvgPrice(newAvgPrice);
        Position saved = positionRepository.saveAndFlush(sameSidePosition);
        log.info("Updated same-side position. positionId={}, userId={}, currencyPair={}, side={}, quantity={}, avgPrice={}",
                saved.getId(), saved.getUserId(), saved.getCurrencyPair(), saved.getSide(), saved.getQuantity(), saved.getAvgPrice());
        return saved;
    }

    public Position reduceOrDeleteOppositePosition(Position oppositeSidePosition, BigDecimal closedQuantity) {
        BigDecimal remaining = oppositeSidePosition.getQuantity().subtract(closedQuantity);
        if (remaining.compareTo(BigDecimal.ZERO) == 0) {
            log.info("Deleting fully closed position. positionId={}, userId={}, currencyPair={}, side={}, closedQuantity={}",
                    oppositeSidePosition.getId(), oppositeSidePosition.getUserId(), oppositeSidePosition.getCurrencyPair(),
                    oppositeSidePosition.getSide(), closedQuantity);
            positionRepository.delete(oppositeSidePosition);
            return null;
        }
        oppositeSidePosition.setQuantity(remaining);
        Position saved = positionRepository.saveAndFlush(oppositeSidePosition);
        log.info("Reduced opposite-side position. positionId={}, userId={}, currencyPair={}, side={}, remainingQuantity={}",
                saved.getId(), saved.getUserId(), saved.getCurrencyPair(), saved.getSide(), saved.getQuantity());
        return saved;
    }

    public BigDecimal weightedAverage(BigDecimal currentAvg,
                                      BigDecimal currentQty,
                                      BigDecimal newPrice,
                                      BigDecimal newQty) {
        BigDecimal totalAmount = currentAvg.multiply(currentQty).add(newPrice.multiply(newQty));
        BigDecimal totalQty = currentQty.add(newQty);
        return totalAmount.divide(totalQty, PRICE_SCALE, RoundingMode.HALF_UP);
    }
}
