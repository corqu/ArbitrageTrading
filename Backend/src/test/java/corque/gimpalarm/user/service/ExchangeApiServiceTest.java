package corque.gimpalarm.user.service;

import corque.gimpalarm.coin.dto.PriceManager;
import corque.gimpalarm.common.util.EncryptionUtil;
import corque.gimpalarm.user.repository.UserCredentialRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class ExchangeApiServiceTest {

    private final ExchangeApiService service = new ExchangeApiService(
            mock(UserCredentialRepository.class),
            mock(EncryptionUtil.class),
            new PriceManager(mock(ApplicationEventPublisher.class))
    );

    @Test
    void normalizeQuantityRoundsDownToStepSize() {
        ExchangeApiService.BinanceSymbolRules rules =
                new ExchangeApiService.BinanceSymbolRules(new BigDecimal("1"), new BigDecimal("0.0001"));

        BigDecimal normalized = service.normalizeQuantity(1234.56789d, rules);

        assertEquals(new BigDecimal("1234"), normalized.stripTrailingZeros());
    }

    @Test
    void normalizePriceRoundsDownToTickSize() {
        ExchangeApiService.BinanceSymbolRules rules =
                new ExchangeApiService.BinanceSymbolRules(new BigDecimal("1"), new BigDecimal("0.0001"));

        BigDecimal normalized = service.normalizePrice(0.01234567d, rules);

        assertEquals(new BigDecimal("0.0123"), normalized.stripTrailingZeros());
    }
}
