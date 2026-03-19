package corque.gimpalarm.userbot.service;

import corque.gimpalarm.coin.dto.TradingRequest;
import corque.gimpalarm.coin.service.TradingBotService;
import corque.gimpalarm.common.exception.NotFoundException;
import corque.gimpalarm.user.domain.User;
import corque.gimpalarm.user.repository.UserRepository;
import corque.gimpalarm.userbot.domain.UserBot;
import corque.gimpalarm.userbot.domain.UserBotStatus;
import corque.gimpalarm.userbot.dto.UserBotResponseDto;
import corque.gimpalarm.userbot.repository.UserBotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserBotServiceTest {

    @Mock
    private UserBotRepository userBotRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TradingBotService tradingBotService;

    private UserBotService userBotService;

    @BeforeEach
    void setUp() {
        userBotService = new UserBotService(userBotRepository, userRepository, tradingBotService);
    }

    @Test
    void getMyBotsReturnsMappedDtos() {
        User user = user();
        UserBot bot = userBot(10L, user);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(userBotRepository.findAllByUser(user)).thenReturn(List.of(bot));

        List<UserBotResponseDto> result = userBotService.getMyBots("user@test.com");

        assertEquals(1, result.size());
        assertEquals("BTC", result.get(0).getSymbol());
        assertEquals(UserBotStatus.WAITING, result.get(0).getStatus());
    }

    @Test
    void subscribeBotSavesBotAndStartsTrading() {
        User user = user();
        TradingRequest request = request();
        UserBot savedBot = userBot(10L, user);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBotRepository.save(any(UserBot.class))).thenReturn(savedBot);

        UserBotResponseDto result = userBotService.subscribeBot(1L, request);

        assertEquals(10L, result.getId());
        assertEquals("BTC", result.getSymbol());
        verify(tradingBotService).executeTradeForUser(1L, request);

        ArgumentCaptor<UserBot> captor = ArgumentCaptor.forClass(UserBot.class);
        verify(userBotRepository).save(captor.capture());
        assertEquals(UserBotStatus.WAITING, captor.getValue().getStatus());
        assertTrue(captor.getValue().isActive());
    }

    @Test
    void updateBotStopsCurrentBotUpdatesFieldsAndRestartsTrading() {
        User user = user();
        UserBot bot = userBot(10L, user);
        TradingRequest request = request();
        request.setAmountKrw(200000.0);
        request.setLeverage(5);
        request.setEntryKimp(1.5);
        request.setExitKimp(4.5);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBotRepository.findByIdAndUser(10L, user)).thenReturn(Optional.of(bot));

        UserBotResponseDto result = userBotService.updateBot(1L, 10L, request);

        assertEquals(200000.0, result.getAmountKrw());
        assertEquals(5, result.getLeverage());
        assertEquals(1.5, result.getEntryKimp());
        assertEquals(4.5, result.getExitKimp());
        assertEquals(UserBotStatus.WAITING, result.getStatus());
        verify(tradingBotService).executeTradeForUser(1L, request);
        verify(tradingBotService).executeTradeForUser(eq(1L), stopRequestMatcher(bot));
    }

    @Test
    void deleteBotStopsCurrentBotAndDeletesIt() {
        User user = user();
        UserBot bot = userBot(10L, user);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(userBotRepository.findByIdAndUser(10L, user)).thenReturn(Optional.of(bot));

        userBotService.deleteBot("user@test.com", 10L);

        verify(tradingBotService).executeTradeForUser(eq(1L), stopRequestMatcher(bot));
        verify(userBotRepository).delete(bot);
    }

    @Test
    void deleteBotThrowsWhenBotMissing() {
        User user = user();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(userBotRepository.findByIdAndUser(10L, user)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> userBotService.deleteBot("user@test.com", 10L));
        verify(userBotRepository, never()).delete(any());
    }

    @Test
    void getMyBotsThrowsWhenUserMissing() {
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> userBotService.getMyBots("user@test.com"));
    }

    private TradingRequest stopRequestMatcher(UserBot bot) {
        TradingRequest stop = new TradingRequest();
        stop.setSymbol(bot.getSymbol());
        stop.setDomesticExchange(bot.getDomesticExchange());
        stop.setForeignExchange(bot.getForeignExchange());
        stop.setAction("STOP");
        return org.mockito.ArgumentMatchers.argThat(req ->
                req != null
                        && "STOP".equals(req.getAction())
                        && bot.getSymbol().equals(req.getSymbol())
                        && bot.getDomesticExchange().equals(req.getDomesticExchange())
                        && bot.getForeignExchange().equals(req.getForeignExchange())
        );
    }

    private User user() {
        return User.builder()
                .id(1L)
                .email("user@test.com")
                .password("pw")
                .nickname("nick")
                .build();
    }

    private UserBot userBot(Long id, User user) {
        return UserBot.builder()
                .id(id)
                .user(user)
                .symbol("BTC")
                .domesticExchange("UPBIT")
                .foreignExchange("BINANCE_FUTURES")
                .amountKrw(100000.0)
                .leverage(3)
                .action("START")
                .entryKimp(2.0)
                .exitKimp(5.0)
                .isActive(true)
                .status(UserBotStatus.WAITING)
                .build();
    }

    private TradingRequest request() {
        TradingRequest request = new TradingRequest();
        request.setSymbol("BTC");
        request.setDomesticExchange("UPBIT");
        request.setForeignExchange("BINANCE_FUTURES");
        request.setAmountKrw(100000.0);
        request.setLeverage(3);
        request.setAction("START");
        request.setEntryKimp(2.0);
        request.setExitKimp(5.0);
        return request;
    }
}


