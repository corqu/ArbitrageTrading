package corque.gimpalarm.user.controller;

import corque.gimpalarm.coin.dto.PriceManager;
import corque.gimpalarm.tradeorder.dto.TradeOrderHistoryRowDto;
import corque.gimpalarm.tradeorder.service.TradeOrderHistoryService;
import corque.gimpalarm.user.domain.UserPrincipal;
import corque.gimpalarm.user.service.ExchangeApiService;
import corque.gimpalarm.user.service.UserCredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCredentialControllerTest {

    @Mock
    private UserCredentialService userCredentialService;
    @Mock
    private ExchangeApiService exchangeApiService;
    @Mock
    private PriceManager priceManager;
    @Mock
    private TradeOrderHistoryService tradeOrderHistoryService;

    private UserCredentialController userCredentialController;

    @BeforeEach
    void setUp() {
        userCredentialController = new UserCredentialController(
                userCredentialService,
                exchangeApiService,
                priceManager,
                tradeOrderHistoryService
        );
    }

    @Test
    void getOrdersReturnsRecentTradeHistoryRows() {
        UserPrincipal principal = UserPrincipal.builder()
                .id(1L)
                .email("user@test.com")
                .password("pw")
                .nickname("nick")
                .build();
        List<TradeOrderHistoryRowDto> rows = List.of(
                TradeOrderHistoryRowDto.builder()
                        .id("10 / 11")
                        .botKey("1:BTC:UPBIT:BINANCE_FUTURES")
                        .symbol("BTC")
                        .phase("ENTRY")
                        .exchange("UPBIT / BINANCE")
                        .requestedQty("2 / 2")
                        .executedQty("2 / 1.5")
                        .remainingQty("0 / 0.5")
                        .requestedPrice("50000 / 40")
                        .averagePrice("50010 / 39.9")
                        .status("done / PARTIALLY_FILLED")
                        .build()
        );
        when(tradeOrderHistoryService.getRecentOrders(1L)).thenReturn(rows);

        var response = userCredentialController.getOrders(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("ENTRY", response.getBody().get(0).getPhase());
        assertEquals("2 / 1.5", response.getBody().get(0).getExecutedQty());
        verify(tradeOrderHistoryService).getRecentOrders(1L);
    }
}
