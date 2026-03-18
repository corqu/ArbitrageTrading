package corque.gimpalarm.tradeorder.service;

import corque.gimpalarm.user.service.ExchangeApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CheckOrderStatusService {

    private final ExchangeApiService exchangeApiService;

    public Map<String, Object> checkUpbitOrderStatus(Long userId, String exchangeOrderId) {
        return exchangeApiService.getOrderUpbit(userId, exchangeOrderId);
    }

    public Map<String, Object> checkBithumbOrderStatus(Long userId, String exchangeOrderId) {
        return exchangeApiService.getOrderBithumb(userId, exchangeOrderId);
    }

    public Map<String, Object> checkBinanceOrderStatus(Long userId, String symbol, String exchangeOrderId) {
        return exchangeApiService.getOrderBinance(userId, symbol, exchangeOrderId);
    }
}
