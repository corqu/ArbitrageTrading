package corque.gimpalarm.coin.service;

import corque.gimpalarm.coin.dto.PriceManager;
import corque.gimpalarm.coin.dto.TradingRequest;
import corque.gimpalarm.user.repository.UserCredentialRepository;
import corque.gimpalarm.user.repository.UserRepository;
import corque.gimpalarm.user.service.ExchangeApiService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class TradingBotServiceTest {

    private final TradingBotService tradingBotService = new TradingBotService(
            mock(KimpService.class),
            mock(UserCredentialRepository.class),
            mock(UserRepository.class),
            mock(ExchangeApiService.class),
            new PriceManager(mock(ApplicationEventPublisher.class))
    );

    @Test
    void resolvePairKeyMapsUpbitBinanceFuturesToKimpKey() {
        TradingRequest request = new TradingRequest();
        request.setDomesticExchange("UPBIT");
        request.setForeignExchange("BINANCE_FUTURES");

        assertEquals("ub-bn", tradingBotService.resolvePairKey(request));
    }

    @Test
    void resolvePairKeyMapsBithumbBybitToKimpKey() {
        TradingRequest request = new TradingRequest();
        request.setDomesticExchange("BITHUMB");
        request.setForeignExchange("BYBIT");

        assertEquals("bt-bb", tradingBotService.resolvePairKey(request));
    }
}
