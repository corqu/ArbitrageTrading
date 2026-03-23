package corque.gimpalarm.tradeorder.service;

import corque.gimpalarm.tradeorder.dto.ExchangeOrderState;
import corque.gimpalarm.tradeorder.dto.OrderStatusCheckResult;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

@Service
public class OrderStateResolverService {

    public OrderStatusCheckResult resolveUpbit(String exchangeOrderId, Map<String, Object> response) {
        return resolveDomestic("UPBIT", null, exchangeOrderId, response);
    }

    public OrderStatusCheckResult resolveBithumb(String exchangeOrderId, Map<String, Object> response) {
        return resolveDomestic("BITHUMB", null, exchangeOrderId, response);
    }

    public OrderStatusCheckResult resolveBinance(String symbol, String exchangeOrderId, Map<String, Object> response) {
        if (response == null) {
            return OrderStatusCheckResult.failure("BINANCE", symbol, exchangeOrderId, "Order fetch returned null response");
        }

        String rawStatus = readString(response, "status");
        ExchangeOrderState resolvedState = switch (normalize(rawStatus)) {
            case "NEW" -> ExchangeOrderState.OPEN;
            case "PARTIALLY_FILLED" -> ExchangeOrderState.PARTIALLY_FILLED;
            case "FILLED" -> ExchangeOrderState.FILLED;
            case "CANCELED", "CANCELLED", "EXPIRED", "REJECTED" -> ExchangeOrderState.CANCELED;
            default -> ExchangeOrderState.UNKNOWN;
        };

        return OrderStatusCheckResult.success("BINANCE", symbol, exchangeOrderId, rawStatus, resolvedState, response);
    }

    private OrderStatusCheckResult resolveDomestic(String exchange, String symbol, String exchangeOrderId, Map<String, Object> response) {
        if (response == null) {
            return OrderStatusCheckResult.failure(exchange, symbol, exchangeOrderId, "Order fetch returned null response");
        }

        String rawStatus = firstNonBlank(readString(response, "state"), readString(response, "status"));
        double executedQty = readDouble(response, "executed_volume");
        double remainingQty = readDouble(response, "remaining_volume");
        ExchangeOrderState resolvedState = resolveDomesticState(rawStatus, executedQty, remainingQty);

        return OrderStatusCheckResult.success(exchange, symbol, exchangeOrderId, rawStatus, resolvedState, response);
    }

    private ExchangeOrderState resolveDomesticState(String rawStatus, double executedQty, double remainingQty) {
        String normalized = normalize(rawStatus);
        if ("DONE".equals(normalized)) {
            return ExchangeOrderState.FILLED;
        }
        if ("CANCEL".equals(normalized) || "CANCELED".equals(normalized) || "CANCELLED".equals(normalized)) {
            return ExchangeOrderState.CANCELED;
        }
        if (executedQty > 0 && remainingQty > 0) {
            return ExchangeOrderState.PARTIALLY_FILLED;
        }
        if ("WAIT".equals(normalized) || "WATCH".equals(normalized)) {
            return ExchangeOrderState.OPEN;
        }
        if (executedQty > 0 && remainingQty == 0) {
            return ExchangeOrderState.FILLED;
        }
        return ExchangeOrderState.UNKNOWN;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String readString(Map<String, Object> response, String key) {
        Object value = response.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private double readDouble(Map<String, Object> response, String key) {
        Object value = response.get(key);
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}