package corque.gimpalarm.tradeorder.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import corque.gimpalarm.common.exception.NotFoundException;
import corque.gimpalarm.tradeorder.domain.TradeOrder;
import corque.gimpalarm.tradeorder.repository.TradeOrderRepository;
import corque.gimpalarm.user.domain.User;
import corque.gimpalarm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class TradeOrderService {

    private final TradeOrderRepository tradeOrderRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void recordOrder(Long userId, String botKey, String exchange, String marketType, String orderRole,
                            String symbol, String side, String positionSide, String orderType,
                            Double requestedQty, Double requestedPrice, Map<String, Object> response) {
        if (response == null) {
            return;
        }

        String exchangeOrderId = extractExchangeOrderId(exchange, response);
        if (exchangeOrderId == null || exchangeOrderId.isBlank()) {
            log.warn("Trade order response had no order id. exchange={}, role={}, symbol={}", exchange, orderRole, symbol);
            return;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        TradeOrder order = TradeOrder.builder()
                .user(user)
                .botKey(botKey)
                .exchange(exchange)
                .marketType(marketType)
                .orderRole(orderRole)
                .symbol(symbol.toUpperCase())
                .side(side)
                .positionSide(positionSide)
                .orderType(orderType)
                .exchangeOrderId(exchangeOrderId)
                .status(extractStatus(exchange, response))
                .requestedQty(requestedQty)
                .executedQty(extractExecutedQty(exchange, response))
                .remainingQty(extractRemainingQty(exchange, response))
                .requestedPrice(requestedPrice)
                .averagePrice(extractAveragePrice(exchange, response))
                .responsePayload(toJson(response))
                .build();

        tradeOrderRepository.save(order);
    }

    @Transactional
    public void updateOrderFromExchange(String exchange, String exchangeOrderId, Map<String, Object> response) {
        if (response == null || exchangeOrderId == null || exchangeOrderId.isBlank()) {
            return;
        }

        tradeOrderRepository.findTopByExchangeAndExchangeOrderIdOrderByIdDesc(exchange, exchangeOrderId)
                .ifPresent(order -> {
                    order.setStatus(extractStatus(exchange, response));
                    Double executedQty = extractExecutedQty(exchange, response);
                    if (executedQty != null) {
                        order.setExecutedQty(executedQty);
                    }
                    Double remainingQty = extractRemainingQty(exchange, response);
                    if (remainingQty != null) {
                        order.setRemainingQty(remainingQty);
                    }
                    Double averagePrice = extractAveragePrice(exchange, response);
                    if (averagePrice != null) {
                        order.setAveragePrice(averagePrice);
                    }
                    order.setResponsePayload(toJson(response));
                });
    }

    @Transactional(readOnly = true)
    public Optional<TradeOrder> getLatestOrder(String botKey, String orderRole) {
        return tradeOrderRepository.findTopByBotKeyAndOrderRoleOrderByIdDesc(botKey, orderRole);
    }

    private String extractExchangeOrderId(String exchange, Map<String, Object> response) {
        if ("BINANCE".equalsIgnoreCase(exchange)) {
            return getString(response, "orderId");
        }
        return firstNonBlank(
                getString(response, "uuid"),
                getString(response, "order_id"),
                getString(response, "id")
        );
    }

    private String extractStatus(String exchange, Map<String, Object> response) {
        if ("BINANCE".equalsIgnoreCase(exchange)) {
            return getString(response, "status");
        }
        return firstNonBlank(getString(response, "state"), getString(response, "status"));
    }

    private Double extractExecutedQty(String exchange, Map<String, Object> response) {
        if ("BINANCE".equalsIgnoreCase(exchange)) {
            return getDouble(response, "executedQty");
        }
        return getDouble(response, "executed_volume");
    }

    private Double extractRemainingQty(String exchange, Map<String, Object> response) {
        if ("BINANCE".equalsIgnoreCase(exchange)) {
            Double origQty = getDouble(response, "origQty");
            Double executedQty = getDouble(response, "executedQty");
            if (origQty != null && executedQty != null) {
                return Math.max(origQty - executedQty, 0.0);
            }
            return null;
        }
        return firstNonNull(getDouble(response, "remaining_volume"), getDouble(response, "volume"));
    }

    private Double extractAveragePrice(String exchange, Map<String, Object> response) {
        if ("BINANCE".equalsIgnoreCase(exchange)) {
            return getDouble(response, "avgPrice");
        }
        return getDouble(response, "price");
    }

    private String toJson(Map<String, Object> response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            return String.valueOf(response);
        }
    }

    private String getString(Map<String, Object> response, String key) {
        Object value = response.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private Double getDouble(Map<String, Object> response, String key) {
        Object value = response.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
