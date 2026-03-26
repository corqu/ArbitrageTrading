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
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class UpbitOrderbookWebSocketService {

    private static final String UPBIT_WSS_URL = "wss://api.upbit.com/websocket/v1";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PriceManager priceManager;
    private final SupportedCoinRepository coinRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        WebSocketClient client = new StandardWebSocketClient();

        client.execute(new AbstractWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                List<String> codes = coinRepository.findAll().stream()
                        .filter(c -> c.isUpbit())
                        .map(c -> "\"KRW-" + c.getSymbol().toUpperCase() + "\"")
                        .collect(Collectors.toList());

                if (codes.isEmpty()) {
                    log.warn("No Upbit symbols available for orderbook subscription.");
                    return;
                }

                String subscribeMessage = String.format(
                        "[{\"ticket\":\"upbit_orderbook\"},{\"type\":\"orderbook\",\"codes\":[%s]}]",
                        String.join(", ", codes)
                );
                session.sendMessage(new TextMessage(subscribeMessage));
                log.info("Subscribed to Upbit orderbooks: {}", codes.size());
            }

            @Override
            protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
                try {
                    String payload = new String(message.getPayload().array(), StandardCharsets.UTF_8);
                    JsonNode root = objectMapper.readTree(payload);
                    String code = root.path("code").asText(null);
                    JsonNode units = root.path("orderbook_units");

                    if (code == null || units.isMissingNode() || !units.isArray() || units.isEmpty()) {
                        return;
                    }

                    JsonNode bestUnit = units.get(0);
                    double askPrice = bestUnit.path("ask_price").asDouble(0.0);
                    double bidPrice = bestUnit.path("bid_price").asDouble(0.0);
                    String symbol = code.split("-")[1].toUpperCase();
                    String key = "UB_" + symbol;

                    priceManager.updateBestAsk(key, askPrice);
                    priceManager.updateBestBid(key, bidPrice);
                } catch (Exception e) {
                    log.debug("Failed to parse Upbit orderbook message: {}", e.getMessage());
                }
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {
                log.error("Upbit orderbook transport error: {}", exception.getMessage());
            }
        }, UPBIT_WSS_URL);
    }
}
