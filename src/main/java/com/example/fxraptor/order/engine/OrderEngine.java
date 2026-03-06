package com.example.fxraptor.order.engine;

import com.example.fxraptor.cache.AccountCache;
import com.example.fxraptor.cache.PositionCache;
import com.example.fxraptor.domain.Account;
import com.example.fxraptor.domain.Position;
import com.example.fxraptor.order.model.MarketOrderCommand;
import com.example.fxraptor.order.model.MarketOrderExecutionResult;
import com.example.fxraptor.order.model.MarketOrderRequest;
import com.example.fxraptor.order.service.MarketOrderService;
import com.example.fxraptor.order.service.NettingService;
import com.example.fxraptor.order.service.TradeService;
import com.example.fxraptor.repository.AccountRepository;
import org.springframework.stereotype.Component;

/**
 * 注文処理の司令塔。サービス呼び出し順とキャッシュ更新のみを担当する。
 */
@Component
public class OrderEngine {

    private final MarketOrderService marketOrderService;
    private final TradeService tradeService;
    private final NettingService nettingService;
    private final PositionCache positionCache;
    private final AccountCache accountCache;
    private final AccountRepository accountRepository;

    public OrderEngine(MarketOrderService marketOrderService,
                       TradeService tradeService,
                       NettingService nettingService,
                       PositionCache positionCache,
                       AccountCache accountCache,
                       AccountRepository accountRepository) {
        this.marketOrderService = marketOrderService;
        this.tradeService = tradeService;
        this.nettingService = nettingService;
        this.positionCache = positionCache;
        this.accountCache = accountCache;
        this.accountRepository = accountRepository;
    }

    public MarketOrderExecutionResult executeMarketOrder(MarketOrderCommand command) {
        MarketOrderExecutionResult result = marketOrderService.execute(new MarketOrderRequest(
                command.userId(),
                command.currencyPair(),
                command.side(),
                command.quantity()
        ));

        tradeService.onMarketOrderExecuted(result);
        nettingService.onMarketOrderExecuted(result);
        updateCaches(command.userId(), result.position());
        return result;
    }

    private void updateCaches(String userId, Position position) {
        if (position != null) {
            positionCache.put(positionKey(position), position);
        }
        Account account = accountRepository.findByUserId(userId).orElse(null);
        if (account != null) {
            accountCache.put(userId, account);
        }
    }

    private String positionKey(Position position) {
        return position.getUserId() + "|" + position.getCurrencyPair() + "|" + position.getSide();
    }
}
