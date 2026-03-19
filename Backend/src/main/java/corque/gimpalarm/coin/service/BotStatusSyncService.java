package corque.gimpalarm.coin.service;

import corque.gimpalarm.botstate.domain.BotTradeState;
import corque.gimpalarm.botstate.service.BotTradeStateService;
import corque.gimpalarm.coin.domain.BotStatus;
import corque.gimpalarm.coin.dto.TradingRequest;
import corque.gimpalarm.userbot.domain.UserBotStatus;
import corque.gimpalarm.userbot.repository.UserBotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class BotStatusSyncService {

    private final UserBotRepository userBotRepository;
    private final BotTradeStateService botTradeStateService;

    @Transactional
    public void sync(Long userId, TradingRequest request, String botKey, BotStatus status) {
        sync(userId, request, botKey, status, status == BotStatus.ERROR ? "Bot entered error state" : null);
    }

    @Transactional
    public void sync(Long userId, TradingRequest request, String botKey, BotStatus status, String errorReason) {
        userBotRepository.findByUserIdAndSymbolIgnoreCaseAndDomesticExchangeIgnoreCaseAndForeignExchangeIgnoreCase(
                        userId,
                        request.getSymbol(),
                        request.getDomesticExchange(),
                        request.getForeignExchange()
                )
                .ifPresentOrElse(userBot -> {
                    userBot.setStatus(UserBotStatus.valueOf(status.name()));
                    userBotRepository.save(userBot);
                    BotTradeState state = botTradeStateService.initialize(userBot, botKey, status);
                    botTradeStateService.updateStatus(state, status, errorReason);
                }, () -> log.warn("UserBot not found for status sync. userId={}, symbol={}, domestic={}, foreign={}",
                        userId,
                        request.getSymbol(),
                        request.getDomesticExchange(),
                        request.getForeignExchange()));
    }
}
