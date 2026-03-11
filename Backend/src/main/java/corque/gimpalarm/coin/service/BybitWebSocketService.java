package corque.gimpalarm.coin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import corque.gimpalarm.coin.dto.bybit.BybitTickerDto;
import corque.gimpalarm.coin.dto.PriceManager;
import corque.gimpalarm.coin.dto.bybit.BybitTickerResponse;
import corque.gimpalarm.common.config.CoinConfig;
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
public class BybitWebSocketService {

    private final String BYBIT_WSS_URL = "wss://stream.bybit.com/v5/public/spot";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PriceManager priceManager;
    private final CoinConfig coinConfig;

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        WebSocketClient client = new StandardWebSocketClient();

        client.execute(new TextWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                String symbols = coinConfig.getCoins().stream()
                        .map(coin -> "\"tickers." + coin.toUpperCase() + "USDT\"")
                        .collect(Collectors.joining(", "));

                String subscribeMessage = String.format("{\"op\": \"subscribe\", \"args\": [%s]}", symbols);
                session.sendMessage(new TextMessage(subscribeMessage));
                log.info("바이비트 소켓 연결 및 구독: {}", symbols);
            }

            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                BybitTickerResponse response = objectMapper.readValue(message.getPayload(), BybitTickerResponse.class);

                if (response.getData() != null) {
                    BybitTickerDto ticker = response.getData();
                    if (ticker.getLastPrice() != null) {
                        double price = Double.parseDouble(ticker.getLastPrice());
                        String key = "BY_" + ticker.getSymbol().replace("USDT", "").toUpperCase();
                        priceManager.updatePrice(key, price);
                    }
                }
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                log.error("바이비트 소켓 에러 발생: {}", exception.getMessage());
            }
        }, BYBIT_WSS_URL);
    }
}
