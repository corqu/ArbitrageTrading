package corque.gimpalarm.botstate.service;

import corque.gimpalarm.botstate.domain.BotTradeState;
import corque.gimpalarm.botstate.repository.BotTradeStateRepository;
import corque.gimpalarm.coin.domain.BotStatus;
import corque.gimpalarm.user.domain.User;
import corque.gimpalarm.userbot.domain.UserBot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BotTradeStateServiceTest {

    @Mock
    private BotTradeStateRepository botTradeStateRepository;

    private BotTradeStateService botTradeStateService;

    @BeforeEach
    void setUp() {
        botTradeStateService = new BotTradeStateService(botTradeStateRepository);
    }

    @Test
    void initializeCreatesStateWhenMissing() {
        User user = User.builder().id(1L).email("a@test.com").password("pw").nickname("nick").build();
        UserBot userBot = UserBot.builder()
                .id(10L)
                .user(user)
                .symbol("BTC")
                .domesticExchange("UPBIT")
                .foreignExchange("BINANCE_FUTURES")
                .amountKrw(100000.0)
                .leverage(3)
                .action("START")
                .build();
        BotTradeState saved = BotTradeState.builder()
                .id(100L)
                .user(user)
                .userBot(userBot)
                .botKey("1:BTC:UPBIT:BINANCE_FUTURES")
                .status(BotStatus.WAITING)
                .build();

        when(botTradeStateRepository.findByUserBotId(10L)).thenReturn(Optional.empty());
        when(botTradeStateRepository.save(any(BotTradeState.class))).thenReturn(saved);

        BotTradeState result = botTradeStateService.initialize(userBot, "1:BTC:UPBIT:BINANCE_FUTURES", BotStatus.WAITING);

        assertSame(saved, result);
        verify(botTradeStateRepository).save(any(BotTradeState.class));
    }

    @Test
    void restoreReturnsWaitingSnapshotWhenStateMissing() {
        when(botTradeStateRepository.findByBotKey("missing")).thenReturn(Optional.empty());

        BotTradeStateService.RestoredTradeSnapshot snapshot = botTradeStateService.restore(1L, "missing");

        assertEquals(BotStatus.WAITING, snapshot.getStatus());
        assertEquals(1L, snapshot.getUserId());
        assertEquals(0.0, snapshot.getFilledQty());
    }

    @Test
    void updateExecutionUpdatesAllExecutionFields() {
        BotTradeState state = BotTradeState.builder().build();
        LocalDateTime now = LocalDateTime.now();

        BotTradeState result = botTradeStateService.updateExecution(
                state, "dom-1", "for-1", 10.0, 7.0, 7.0, 120.0, 100.0, now
        );

        assertSame(state, result);
        assertEquals("dom-1", state.getDomesticOrderId());
        assertEquals("for-1", state.getForeignOrderId());
        assertEquals(10.0, state.getTotalTargetQty());
        assertEquals(7.0, state.getFilledQty());
        assertEquals(7.0, state.getHedgedQty());
        assertEquals(120.0, state.getSlPrice());
        assertEquals(100.0, state.getTpPrice());
        assertEquals(now, state.getEntryTime());
        assertNotNull(state.getLastCheckedAt());
    }

    @Test
    void findActiveStatesUsesConfiguredStatuses() {
        when(botTradeStateRepository.findAllByStatusIn(any())).thenReturn(List.of(BotTradeState.builder().build()));

        List<BotTradeState> states = botTradeStateService.findActiveStates();

        assertEquals(1, states.size());
        verify(botTradeStateRepository).findAllByStatusIn(any());
    }
}
