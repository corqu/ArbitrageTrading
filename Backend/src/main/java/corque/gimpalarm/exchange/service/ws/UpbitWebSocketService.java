package corque.gimpalarm.exchange.service.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import corque.gimpalarm.coin.dto.PriceManager;
import corque.gimpalarm.coin.dto.UpbitTickerDto;
import corque.gimpalarm.coin.repository.SupportedCoinRepository;
import corque.gimpalarm.coin.service.UsdKrwCacheService;
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
public class UpbitWebSocketService {

    private final String UPBIT_WSS_URL = "wss://api.upbit.com/websocket/v1";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PriceManager priceManager;
    private final BinanceFuturesWebSocketService binanceFuturesWebSocketService;
    private final UsdKrwCacheService usdKrwCacheService;
    private final SupportedCoinRepository coinRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        log.info("Starting Upbit websocket initialization");

        restoreCachedUsdKrw();
        fetchInitialUsdKrw();
        connect();
        binanceFuturesWebSocketService.connect();
    }

    private void restoreCachedUsdKrw() {
        usdKrwCacheService.getLatest().ifPresent(price -> {
            priceManager.updateUsdKrw(price);
            log.info("Restored KRW-USDT from InfluxDB: {}", price);
        });
    }

    private void fetchInitialUsdKrw() {
        try {
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            String url = "https://api.upbit.com/v1/ticker?markets=KRW-USDT";

            java.util.List response = restTemplate.getForObject(url, java.util.List.class);

            if (response != null && !response.isEmpty()) {
                java.util.Map<String, Object> ticker = (java.util.Map<String, Object>) response.get(0);
                Object tradePriceObj = ticker.get("trade_price");

                if (tradePriceObj != null) {
                    double price = Double.parseDouble(tradePriceObj.toString());
                    priceManager.updateUsdKrw(price);
                    usdKrwCacheService.save(price, "upbit_rest");
                    log.info("Loaded initial KRW-USDT from Upbit REST: {}", price);
                }
            }
        } catch (Exception e) {
            log.error("Failed to load initial KRW-USDT from Upbit REST: {}", e.getMessage());
            if (!priceManager.hasCurrentUsdKrw()) {
                priceManager.updateUsdKrw(1450.0);
                log.warn("No cached KRW-USDT value found. Falling back to 1450.0");
            }
        }
    }

    private void connect() {
        WebSocketClient client = new StandardWebSocketClient();

        client.execute(new AbstractWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                List<String> codes = coinRepository.findAll().stream()
                        .filter(c -> c.isUpbit())
                        .map(c -> "KRW-" + c.getSymbol().toUpperCase())
                        .collect(Collectors.toList());

                codes.add("KRW-USDT");

                String coinJson = codes.stream()
                        .map(code -> "\"" + code + "\"")
                        .collect(Collectors.joining(", "));

                String subscribeMessage = String.format("[{\"ticket\":\"my_kimp_project\"},{\"type\":\"ticker\",\"codes\":[%s]}]", coinJson);
                session.sendMessage(new TextMessage(subscribeMessage));
                log.info("Subscribed to Upbit websocket tickers: {}", codes.size());
            }

            @Override
            protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
                String payload = new String(message.getPayload().array(), StandardCharsets.UTF_8);
                UpbitTickerDto ticker = objectMapper.readValue(payload, UpbitTickerDto.class);

                if (ticker.getCode() != null) {
                    double price = ticker.getTradePrice();
                    if ("KRW-USDT".equals(ticker.getCode())) {
                        priceManager.updateUsdKrw(price);
                        usdKrwCacheService.save(price, "upbit_ws");
                    } else {
                        String symbol = ticker.getCode().split("-")[1].toUpperCase();
                        priceManager.updatePrice("UB_" + symbol, price);
                        priceManager.updateTradeVolume("UB_" + symbol, ticker.getAccTradePrice24h());
                    }
                }
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                log.error("Upbit websocket transport error: {}", exception.getMessage());
            }
        }, UPBIT_WSS_URL);
    }
}
