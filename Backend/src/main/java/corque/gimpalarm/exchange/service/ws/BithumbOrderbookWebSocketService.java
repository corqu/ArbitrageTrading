package corque.gimpalarm.exchange.service.ws;

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

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BithumbOrderbookWebSocketService {

    private static final String BITHUMB_WSS_URL = "wss://pubwss.bithumb.com/pub/ws";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PriceManager priceManager;
    private final SupportedCoinRepository coinRepository;

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

                session.sendMessage(new TextMessage(buildSnapshotRequestMessage(symbols)));
                log.info("Bithumb orderbook snapshot subscribed for {} symbols", symbols.size());
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
                        publishSnapshotQuote(symbol, content);
                    }
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

    private String buildSnapshotRequestMessage(List<String> symbols) {
        return String.format(
                "{\"type\":\"orderbooksnapshot\", \"symbols\": [%s], \"isOnlySnapshot\": true}",
                String.join(", ", symbols)
        );
    }

    private void publishSnapshotQuote(String symbol, JsonNode content) {
        double bestAsk = extractBestAsk(content.get("asks"));
        double bestBid = extractBestBid(content.get("bids"));
        String key = "BT_" + symbol;

        if (bestAsk > 0) {
            priceManager.updateBestAsk(key, bestAsk);
        }
        if (bestBid > 0) {
            priceManager.updateBestBid(key, bestBid);
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

    private double extractBestAsk(JsonNode levels) {
        if (levels == null || !levels.isArray()) {
            return 0.0;
        }

        double bestAsk = Double.MAX_VALUE;
        for (JsonNode level : levels) {
            double price = parseSnapshotPrice(level);
            double quantity = parseSnapshotQuantity(level);
            if (price > 0 && quantity > 0 && price < bestAsk) {
                bestAsk = price;
            }
        }

        return bestAsk == Double.MAX_VALUE ? 0.0 : bestAsk;
    }

    private double extractBestBid(JsonNode levels) {
        if (levels == null || !levels.isArray()) {
            return 0.0;
        }

        double bestBid = 0.0;
        for (JsonNode level : levels) {
            double price = parseSnapshotPrice(level);
            double quantity = parseSnapshotQuantity(level);
            if (price > 0 && quantity > 0 && price > bestBid) {
                bestBid = price;
            }
        }

        return bestBid;
    }

    private double parseSnapshotPrice(JsonNode level) {
        if (level == null || level.isNull()) {
            return 0.0;
        }
        if (level.isArray() && level.size() >= 1) {
            return parseNodeDouble(level.get(0));
        }
        return parseFieldDouble(level, "price");
    }

    private double parseSnapshotQuantity(JsonNode level) {
        if (level == null || level.isNull()) {
            return 0.0;
        }
        if (level.isArray() && level.size() >= 2) {
            return parseNodeDouble(level.get(1));
        }
        return parseFieldDouble(level, "quantity");
    }

    private double parseNodeDouble(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(node.asText());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private double parseFieldDouble(JsonNode node, String fieldName) {
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
