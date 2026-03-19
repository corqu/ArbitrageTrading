package corque.gimpalarm.coin.service;

import corque.gimpalarm.botstate.domain.BotTradeState;
import corque.gimpalarm.botstate.service.BotTradeStateService;
import corque.gimpalarm.coin.domain.BotStatus;
import corque.gimpalarm.coin.dto.TradingRequest;
import corque.gimpalarm.user.domain.User;
import corque.gimpalarm.userbot.domain.UserBot;
import corque.gimpalarm.userbot.domain.UserBotStatus;
import corque.gimpalarm.userbot.repository.UserBotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BotStatusSyncServiceTest {

    @Mock
    private UserBotRepository userBotRepository;
    @Mock
    private BotTradeStateService botTradeStateService;

    private BotStatusSyncService botStatusSyncService;

    @BeforeEach
    void setUp() {
        botStatusSyncService = new BotStatusSyncService(userBotRepository, botTradeStateService);
    }

    @Test
    void syncUpdatesUserBotAndStateWhenBotExists() {
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
        TradingRequest request = new TradingRequest();
        request.setSymbol("BTC");
        request.setDomesticExchange("UPBIT");
        request.setForeignExchange("BINANCE_FUTURES");
        BotTradeState state = BotTradeState.builder().user(user).userBot(userBot).build();

        when(userBotRepository.findByUserIdAndSymbolIgnoreCaseAndDomesticExchangeIgnoreCaseAndForeignExchangeIgnoreCase(
                1L, "BTC", "UPBIT", "BINANCE_FUTURES"
        )).thenReturn(Optional.of(userBot));
        when(botTradeStateService.initialize(userBot, "1:BTC:UPBIT:BINANCE_FUTURES", BotStatus.HOLDING)).thenReturn(state);

        botStatusSyncService.sync(1L, request, "1:BTC:UPBIT:BINANCE_FUTURES", BotStatus.HOLDING);

        verify(userBotRepository).save(userBot);
        verify(botTradeStateService).initialize(userBot, "1:BTC:UPBIT:BINANCE_FUTURES", BotStatus.HOLDING);
        verify(botTradeStateService).updateStatus(state, BotStatus.HOLDING, null);
    }

    @Test
    void syncDoesNothingWhenUserBotMissing() {
        TradingRequest request = new TradingRequest();
        request.setSymbol("BTC");
        request.setDomesticExchange("UPBIT");
        request.setForeignExchange("BINANCE_FUTURES");

        when(userBotRepository.findByUserIdAndSymbolIgnoreCaseAndDomesticExchangeIgnoreCaseAndForeignExchangeIgnoreCase(
                1L, "BTC", "UPBIT", "BINANCE_FUTURES"
        )).thenReturn(Optional.empty());

        botStatusSyncService.sync(1L, request, "1:BTC:UPBIT:BINANCE_FUTURES", BotStatus.ERROR);

        verify(userBotRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(botTradeStateService, never()).initialize(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
