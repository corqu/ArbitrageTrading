package corque.gimpalarm.tradeorder.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class OrderStatusCheckResult {

    private final String exchange;
    private final String symbol;
    private final String exchangeOrderId;
    private final boolean fetchSucceeded;
    private final String rawStatus;
    private final ExchangeOrderState resolvedState;
    private final String failureReason;
    private final Map<String, Object> response;

    public static OrderStatusCheckResult success(String exchange, String symbol, String exchangeOrderId,
                                                 String rawStatus, ExchangeOrderState resolvedState,
                                                 Map<String, Object> response) {
        return OrderStatusCheckResult.builder()
                .exchange(exchange)
                .symbol(symbol)
                .exchangeOrderId(exchangeOrderId)
                .fetchSucceeded(true)
                .rawStatus(rawStatus)
                .resolvedState(resolvedState)
                .response(response)
                .build();
    }

    public static OrderStatusCheckResult failure(String exchange, String symbol, String exchangeOrderId, String failureReason) {
        return OrderStatusCheckResult.builder()
                .exchange(exchange)
                .symbol(symbol)
                .exchangeOrderId(exchangeOrderId)
                .fetchSucceeded(false)
                .resolvedState(ExchangeOrderState.FETCH_FAILED)
                .failureReason(failureReason)
                .build();
    }
}