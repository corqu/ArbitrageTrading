package corque.gimpalarm.coin.service;

import corque.gimpalarm.botstate.service.BotTradeStateService;
import corque.gimpalarm.coin.domain.BotStatus;
import corque.gimpalarm.coin.dto.KimpResponseDto;
import corque.gimpalarm.coin.dto.PriceChangedEvent;
import corque.gimpalarm.coin.dto.PriceManager;
import corque.gimpalarm.coin.dto.TradingRequest;
import corque.gimpalarm.common.exception.NotFoundException;
import corque.gimpalarm.tradeorder.service.TradeOrderService;
import corque.gimpalarm.user.domain.User;
import corque.gimpalarm.user.repository.UserRepository;
import corque.gimpalarm.user.service.ExchangeApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradingBotServiceTest {

    @Mock
    private KimpService kimpService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ExchangeApiService exchangeApiService;
    @Mock
    private TradeOrderService tradeOrderService;
    @Mock
    private BotTradeStateService botTradeStateService;
    @Mock
    private BotStatusSyncService botStatusSyncService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TradingBotService tradingBotService;
    private PriceManager priceManager;

    @BeforeEach
    void setUp() {
        priceManager = new PriceManager(eventPublisher);
        tradingBotService = new TradingBotService(
                kimpService,
                userRepository,
                exchangeApiService,
                priceManager,
                tradeOrderService,
                botTradeStateService,
                botStatusSyncService
        );
    }

    @Test
    @DisplayName("거래소 명칭이 내부 시스템에 맞게 변경되는지 확인")
    void resolvePairKeyMapsUpbitBinanceFuturesToKimpKey() {
        TradingRequest request = new TradingRequest();
        request.setDomesticExchange("UPBIT");
        request.setForeignExchange("BINANCE_FUTURES");

        assertEquals("ub-bn", tradingBotService.resolvePairKey(request));
    }

    @Test
    @DisplayName("사용자가 없을 때 에러를 발생하는지 확인")
    void executeTradeForUserThrowsWhenUserMissing() {
        TradingRequest request = baseRequest();
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> tradingBotService.executeTradeForUser(1L, request));
    }

    @Test
    @DisplayName("봇 구독해제시 봇이 잘 멈추고 제거되는지 확인")
    void executeTradeForUserStopRemovesBotAndSyncsStopped() {
        User user = User.builder().id(1L).email("a@test.com").build();
        TradingRequest request = baseRequest();
        request.setAction("STOP");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        String result = tradingBotService.executeTradeForUser(1L, request);

        assertTrue(result.endsWith("stopped"));
        assertTrue(tradingBotService.getBotStatus().isEmpty());
        verify(botStatusSyncService).sync(1L, request, "1:BTC:UPBIT:BINANCE_FUTURES", BotStatus.STOPPED);
    }

    @Test
    @DisplayName("설정 가격에 도달했을 때 주문이 잘 들어가는지 확인")
    void onPriceChangedEntersTradeWhenEntryConditionMatches() {
        User user = User.builder().id(1L).email("a@test.com").build();
        TradingRequest request = baseRequest();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(kimpService.calculateAllPairs()).thenReturn(Map.of(
                "ub-bn", List.of(KimpResponseDto.builder()
                        .symbol("BTC")
                        .standardRatio(1.0)
                        .build())
        ));
        when(exchangeApiService.orderUpbit(1L, "BTC", "bid", 2.0, 50000.0, "limit"))
                .thenReturn(Map.of("uuid", "upbit-order-1"));
        when(exchangeApiService.orderBinanceFutures(1L, "BTC", "SELL", "SHORT", 2.0, 40.0, "LIMIT"))
                .thenReturn(Map.of("orderId", 12345L));
        when(botTradeStateService.findByBotKey("1:BTC:UPBIT:BINANCE_FUTURES")).thenReturn(Optional.empty());

        priceManager.updatePrice("UB_BTC", 50000.0);
        priceManager.updatePrice("BN_F_BTC", 40.0);
        tradingBotService.executeTradeForUser(1L, request);
        tradingBotService.onPriceChanged(new PriceChangedEvent("UB_BTC", 50000.0));

        verify(exchangeApiService).setBinanceLeverage(1L, "BTC", 3);
        verify(tradeOrderService).recordOrder(eq(1L), eq("1:BTC:UPBIT:BINANCE_FUTURES"), eq("UPBIT"),
                eq("SPOT"), eq("ENTRY_DOMESTIC"), eq("BTC"), eq("BUY"), isNull(), eq("LIMIT"),
                eq(2.0), eq(50000.0), any());
        verify(tradeOrderService).recordOrder(eq(1L), eq("1:BTC:UPBIT:BINANCE_FUTURES"), eq("BINANCE"),
                eq("FUTURES"), eq("ENTRY_FOREIGN"), eq("BTC"), eq("SELL"), eq("SHORT"), eq("LIMIT"),
                eq(2.0), eq(40.0), any());
        verify(botStatusSyncService).sync(1L, request, "1:BTC:UPBIT:BINANCE_FUTURES", BotStatus.ENTRY_PENDING);
    }

    @Test
    void onPriceChangedDoesNotEnterTwiceWhenReentrantPriceEventArrives() {
        User user = User.builder().id(1L).email("a@test.com").build();
        TradingRequest request = baseRequest();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(kimpService.calculateAllPairs()).thenReturn(Map.of(
                "ub-bn", List.of(KimpResponseDto.builder()
                        .symbol("BTC")
                        .standardRatio(1.0)
                        .entryRatio(1.0)
                        .build())
        ));
        doAnswer(invocation -> {
            tradingBotService.onPriceChanged(new PriceChangedEvent("UB_BTC", 50000.0));
            return Map.of("uuid", "upbit-order-1");
        }).when(exchangeApiService).orderUpbit(1L, "BTC", "bid", 2.0, 50000.0, "limit");
        when(exchangeApiService.orderBinanceFutures(1L, "BTC", "SELL", "SHORT", 2.0, 40.0, "LIMIT"))
                .thenReturn(Map.of("orderId", 12345L));
        when(botTradeStateService.findByBotKey("1:BTC:UPBIT:BINANCE_FUTURES")).thenReturn(Optional.empty());

        priceManager.updatePrice("UB_BTC", 50000.0);
        priceManager.updatePrice("BN_F_BTC", 40.0);
        tradingBotService.executeTradeForUser(1L, request);

        tradingBotService.onPriceChanged(new PriceChangedEvent("UB_BTC", 50000.0));

        verify(exchangeApiService).orderUpbit(1L, "BTC", "bid", 2.0, 50000.0, "limit");
        verify(exchangeApiService).orderBinanceFutures(1L, "BTC", "SELL", "SHORT", 2.0, 40.0, "LIMIT");
    }

    @Test
    void onPriceChangedSkipsEntryWhenPricesAreMissing() {
        User user = User.builder().id(1L).email("a@test.com").build();
        TradingRequest request = baseRequest();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(kimpService.calculateAllPairs()).thenReturn(Map.of(
                "ub-bn", List.of(KimpResponseDto.builder()
                        .symbol("BTC")
                        .standardRatio(1.0)
                        .build())
        ));

        tradingBotService.executeTradeForUser(1L, request);
        tradingBotService.onPriceChanged(new PriceChangedEvent("UB_BTC", 50000.0));

        verify(exchangeApiService, never()).orderUpbit(anyLong(), any(), any(), anyDouble(), any(), any());
        verify(tradeOrderService, never()).recordOrder(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("주문 실패 시 포지션 정리 후 봇이 대기상태로 돌아가는지 확인")
    void processOngoingTradesReturnsToWaitingWhenFailsafeCloseSucceeds() {
        User user = User.builder().id(1L).email("a@test.com").build();
        TradingRequest request = baseRequest();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(kimpService.calculateAllPairs()).thenReturn(Map.of(
                "ub-bn", List.of(KimpResponseDto.builder().symbol("BTC").standardRatio(1.0).build())
        ));
        when(exchangeApiService.orderUpbit(1L, "BTC", "bid", 2.0, 50000.0, "limit"))
                .thenReturn(Map.of("uuid", "upbit-order-1"));
        when(exchangeApiService.orderBinanceFutures(1L, "BTC", "SELL", "SHORT", 2.0, 40.0, "LIMIT"))
                .thenReturn(Map.of("orderId", 12345L));
        when(botTradeStateService.findByBotKey("1:BTC:UPBIT:BINANCE_FUTURES")).thenReturn(Optional.empty());

        priceManager.updatePrice("UB_BTC", 50000.0);
        priceManager.updatePrice("BN_F_BTC", 40.0);
        tradingBotService.executeTradeForUser(1L, request);
        tradingBotService.onPriceChanged(new PriceChangedEvent("UB_BTC", 50000.0));

        when(exchangeApiService.getOrderUpbit(1L, "upbit-order-1"))
                .thenReturn(Map.of("state", "wait", "executed_volume", "0.0"));
        when(exchangeApiService.getOrderBinance(1L, "BTC", "12345"))
                .thenReturn(Map.of("status", "FILLED", "executedQty", "2.0", "avgPrice", "40.0"));
        when(exchangeApiService.orderBinanceFutures(1L, "BTC", "BUY", "SHORT", 2.0, null, "MARKET"))
                .thenReturn(Map.of("orderId", 99999L));

        Map<String, ?> activeBots = (Map<String, ?>) ReflectionTestUtils.getField(tradingBotService, "activeBots");
        Object activeTrade = activeBots.get("1:BTC:UPBIT:BINANCE_FUTURES");
        ReflectionTestUtils.setField(activeTrade, "entryTime", LocalDateTime.now().minusSeconds(181));

        tradingBotService.processOngoingTrades();

        verify(exchangeApiService).orderBinanceFutures(1L, "BTC", "BUY", "SHORT", 2.0, null, "MARKET");
        verify(botStatusSyncService, atLeastOnce()).sync(1L, request, "1:BTC:UPBIT:BINANCE_FUTURES", BotStatus.WAITING);
        verify(botStatusSyncService, never()).sync(1L, request, "1:BTC:UPBIT:BINANCE_FUTURES", BotStatus.ERROR);
        assertTrue(tradingBotService.getBotStatus().containsKey("1:BTC:UPBIT:BINANCE_FUTURES"));
    }

    @Test
    void onPriceChangedSkipsExitWhenOnlyStandardRatioMatchesButExitRatioDoesNot() {
        User user = User.builder().id(1L).email("a@test.com").build();
        TradingRequest request = baseRequest();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(kimpService.calculateAllPairs()).thenReturn(Map.of(
                "ub-bn", List.of(KimpResponseDto.builder().symbol("BTC").standardRatio(6.0).exitRatio(4.0).build())
        ));

        tradingBotService.executeTradeForUser(1L, request);
        Map<String, ?> activeBots = (Map<String, ?>) ReflectionTestUtils.getField(tradingBotService, "activeBots");
        Object activeTrade = activeBots.get("1:BTC:UPBIT:BINANCE_FUTURES");
        ReflectionTestUtils.setField(activeTrade, "status", BotStatus.HOLDING);
        ReflectionTestUtils.setField(activeTrade, "filledQty", 2.0);
        ReflectionTestUtils.setField(activeTrade, "hedgedQty", 2.0);

        tradingBotService.onPriceChanged(new PriceChangedEvent("UB_BTC", 50000.0));

        verify(exchangeApiService, never()).orderUpbit(anyLong(), any(), any(), anyDouble(), any(), any());
        verify(exchangeApiService, never()).orderBinanceFutures(anyLong(), any(), any(), any(), anyDouble(), any(), any());
        verify(botStatusSyncService, never()).sync(1L, request, "1:BTC:UPBIT:BINANCE_FUTURES", BotStatus.STOPPED);
    }

    @Test
    void onPriceChangedExitsTradeWhenExitRatioMatches() {
        User user = User.builder().id(1L).email("a@test.com").build();
        TradingRequest request = baseRequest();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(kimpService.calculateAllPairs()).thenReturn(Map.of(
                "ub-bn", List.of(KimpResponseDto.builder().symbol("BTC").standardRatio(4.0).exitRatio(6.0).build())
        ));
        when(exchangeApiService.orderUpbit(1L, "BTC", "ask", 2.0, null, "market"))
                .thenReturn(Map.of("uuid", "exit-upbit-order-1"));
        when(exchangeApiService.orderBinanceFutures(1L, "BTC", "BUY", "SHORT", 2.0, null, "MARKET"))
                .thenReturn(Map.of("orderId", 54321L));

        tradingBotService.executeTradeForUser(1L, request);
        Map<String, ?> activeBots = (Map<String, ?>) ReflectionTestUtils.getField(tradingBotService, "activeBots");
        Object activeTrade = activeBots.get("1:BTC:UPBIT:BINANCE_FUTURES");
        ReflectionTestUtils.setField(activeTrade, "status", BotStatus.HOLDING);
        ReflectionTestUtils.setField(activeTrade, "filledQty", 2.0);
        ReflectionTestUtils.setField(activeTrade, "hedgedQty", 2.0);

        tradingBotService.onPriceChanged(new PriceChangedEvent("UB_BTC", 50000.0));

        verify(exchangeApiService).orderUpbit(1L, "BTC", "ask", 2.0, null, "market");
        verify(exchangeApiService).orderBinanceFutures(1L, "BTC", "BUY", "SHORT", 2.0, null, "MARKET");
        verify(botStatusSyncService).sync(1L, request, "1:BTC:UPBIT:BINANCE_FUTURES", BotStatus.STOPPED);
    }

    private TradingRequest baseRequest() {
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

