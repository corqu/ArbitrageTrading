package corque.gimpalarm.coin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import corque.gimpalarm.coin.dto.PriceManager;
import corque.gimpalarm.coin.repository.SupportedCoinRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BithumbOrderbookWebSocketService {

    private static final String BITHUMB_WSS_URL = "wss://pubwss.bithumb.com/pub/ws";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PriceManager priceManager;
    private final SupportedCoinRepository coinRepository;

    private final Map<String, NavigableMap<Double, Double>> askBooks = new ConcurrentHashMap<>();
    private final Map<String, NavigableMap<Double, Double>> bidBooks = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        WebSocketClient client = new StandardWebSocketClient();

        client.execute(new TextWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                List<String> symbols = coinRepository.findAll().stream()
                        .filter(c -> c.isBithumb())
                        .map(c -> "\"" + c.getSymbol().toUpperCase() + "_KRW\"")
                        .collect(Collectors.toList());

                if (symbols.isEmpty()) {
                    log.warn("No Bithumb symbols available for orderbook subscription.");
                    return;
                }

                String subscribeMessage = String.format(
                        "{\"type\":\"orderbookdepth\", \"symbols\": [%s]}",
                        String.join(", ", symbols)
                );
                session.sendMessage(new TextMessage(subscribeMessage));
                log.info("Bithumb orderbook subscribed for {} symbols", symbols.size());
            }

            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                try {
                    JsonNode root = objectMapper.readTree(message.getPayload());
                    JsonNode content = root.get("content");
                    if (content == null || content.isNull()) {
                        return;
                    }

                    String symbol = extractSymbol(content);
                    if (symbol == null) {
                        return;
                    }

                    if (content.has("asks") || content.has("bids")) {
                        applySnapshot(symbol, content);
                    } else if (content.has("list")) {
                        applyDepthUpdate(symbol, content.get("list"));
                    }

                    publishBestQuote(symbol);
                } catch (Exception e) {
                    log.debug("Failed to parse Bithumb orderbook message: {}", e.getMessage());
                }
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {
                log.error("Bithumb orderbook transport error: {}", exception.getMessage());
            }
        }, BITHUMB_WSS_URL);
    }

    private void applySnapshot(String symbol, JsonNode content) {
        NavigableMap<Double, Double> asks = new ConcurrentSkipListMap<>();
        NavigableMap<Double, Double> bids = new ConcurrentSkipListMap<>(Comparator.reverseOrder());

        fillBook(asks, content.get("asks"));
        fillBook(bids, content.get("bids"));

        askBooks.put(symbol, asks);
        bidBooks.put(symbol, bids);
    }

    private void applyDepthUpdate(String symbol, JsonNode entries) {
        NavigableMap<Double, Double> asks = askBooks.computeIfAbsent(symbol, key -> new ConcurrentSkipListMap<>());
        NavigableMap<Double, Double> bids = bidBooks.computeIfAbsent(symbol, key -> new ConcurrentSkipListMap<>(Comparator.reverseOrder()));

        for (JsonNode entry : entries) {
            String orderType = text(entry, "orderType");
            double price = parseDouble(entry, "price");
            double quantity = parseDouble(entry, "quantity");
            if (price <= 0) {
                continue;
            }

            NavigableMap<Double, Double> book = "ask".equalsIgnoreCase(orderType) ? asks : bids;
            if (quantity <= 0) {
                book.remove(price);
            } else {
                book.put(price, quantity);
            }
        }
    }

    private void fillBook(NavigableMap<Double, Double> book, JsonNode levels) {
        if (levels == null || !levels.isArray()) {
            return;
        }

        for (JsonNode level : levels) {
            double price = parseDouble(level, "price");
            double quantity = parseDouble(level, "quantity");
            if (price > 0 && quantity > 0) {
                book.put(price, quantity);
            }
        }
    }

    private void publishBestQuote(String symbol) {
        NavigableMap<Double, Double> asks = askBooks.get(symbol);
        if (asks != null && !asks.isEmpty()) {
            priceManager.updateBestAsk("BT_" + symbol, asks.firstKey());
        }

        NavigableMap<Double, Double> bids = bidBooks.get(symbol);
        if (bids != null && !bids.isEmpty()) {
            priceManager.updateBestBid("BT_" + symbol, bids.firstKey());
        }
    }

    private String extractSymbol(JsonNode content) {
        String symbolValue = text(content, "symbol");
        if (symbolValue == null || symbolValue.isBlank()) {
            JsonNode list = content.get("list");
            if (list != null && list.isArray() && !list.isEmpty()) {
                symbolValue = text(list.get(0), "symbol");
            }
        }

        if (symbolValue == null || symbolValue.isBlank()) {
            return null;
        }

        int separatorIndex = symbolValue.indexOf('_');
        return separatorIndex >= 0 ? symbolValue.substring(0, separatorIndex).toUpperCase() : symbolValue.toUpperCase();
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return value != null && !value.isNull() ? value.asText() : null;
    }

    private double parseDouble(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        if (value == null || value.isBlank()) {
            return 0.0;
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
