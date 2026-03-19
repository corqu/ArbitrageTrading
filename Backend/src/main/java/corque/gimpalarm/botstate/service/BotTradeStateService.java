package corque.gimpalarm.botstate.service;

import corque.gimpalarm.botstate.domain.BotTradeState;
import corque.gimpalarm.botstate.repository.BotTradeStateRepository;
import corque.gimpalarm.coin.domain.BotStatus;
import corque.gimpalarm.userbot.domain.UserBot;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BotTradeStateService {

    private final BotTradeStateRepository botTradeStateRepository;

    @Transactional
    public BotTradeState initialize(UserBot userBot, String botKey, BotStatus status) {
        return botTradeStateRepository.findByUserBotId(userBot.getId())
                .map(existing -> {
                    existing.setBotKey(botKey);
                    return updateStatus(existing, status, null);
                })
                .orElseGet(() -> botTradeStateRepository.save(BotTradeState.builder()
                        .userBot(userBot)
                        .user(userBot.getUser())
                        .botKey(botKey)
                        .status(status)
                        .filledQty(0.0)
                        .hedgedQty(0.0)
                        .totalTargetQty(0.0)
                        .build()));
    }

    @Transactional(readOnly = true)
    public RestoredTradeSnapshot restore(Long userId, String botKey) {
        return botTradeStateRepository.findByBotKey(botKey)
                .map(state -> new RestoredTradeSnapshot(
                        state.getStatus(),
                        userId,
                        state.getDomesticOrderId(),
                        state.getForeignOrderId(),
                        valueOrZero(state.getTotalTargetQty()),
                        valueOrZero(state.getFilledQty()),
                        valueOrZero(state.getHedgedQty()),
                        state.getSlPrice(),
                        state.getTpPrice(),
                        state.getEntryTime()
                ))
                .orElseGet(() -> new RestoredTradeSnapshot(BotStatus.WAITING, userId, null, null, 0.0, 0.0, 0.0, null, null, null));
    }

    @Transactional(readOnly = true)
    public Optional<BotTradeState> findByBotKey(String botKey) {
        return botTradeStateRepository.findByBotKey(botKey);
    }

    @Transactional(readOnly = true)
    public List<BotTradeState> findActiveStates() {
        return botTradeStateRepository.findAllByStatusIn(List.of(
                BotStatus.WAITING,
                BotStatus.ENTERING,
                BotStatus.HOLDING,
                BotStatus.EXITING,
                BotStatus.ERROR
        ));
    }

    @Transactional
    public BotTradeState updateStatus(BotTradeState state, BotStatus status, String errorReason) {
        state.setStatus(status);
        state.setErrorReason(errorReason);
        state.setLastCheckedAt(LocalDateTime.now());
        return state;
    }

    @Transactional
    public BotTradeState updateExecution(BotTradeState state,
                                         String domesticOrderId,
                                         String foreignOrderId,
                                         Double totalTargetQty,
                                         Double filledQty,
                                         Double hedgedQty,
                                         Double slPrice,
                                         Double tpPrice,
                                         LocalDateTime entryTime) {
        state.setDomesticOrderId(domesticOrderId);
        state.setForeignOrderId(foreignOrderId);
        state.setTotalTargetQty(totalTargetQty);
        state.setFilledQty(filledQty);
        state.setHedgedQty(hedgedQty);
        state.setSlPrice(slPrice);
        state.setTpPrice(tpPrice);
        state.setEntryTime(entryTime);
        state.setLastCheckedAt(LocalDateTime.now());
        return state;
    }

    private double valueOrZero(Double value) {
        return value != null ? value : 0.0;
    }

    @Getter
    @AllArgsConstructor
    public static class RestoredTradeSnapshot {
        private final BotStatus status;
        private final Long userId;
        private final String domesticOrderId;
        private final String foreignOrderId;
        private final double totalTargetQty;
        private final double filledQty;
        private final double hedgedQty;
        private final Double slPrice;
        private final Double tpPrice;
        private final LocalDateTime entryTime;
    }
}
