package corque.gimpalarm.coin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import corque.gimpalarm.coin.dto.BybitTickerDto;
import corque.gimpalarm.coin.dto.PriceManager;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class BybitWebSocketService {

    private final String BYBIT_WSS_URL = "wss://stream.bybit.com/v5/public/spot";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PriceManager priceManager;

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        WebSocketClient client = new StandardWebSocketClient();

        client.execute(new TextWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                log.info("바이비트 소켓 연결");
                String subscribeMessage = "{\"op\": \"subscribe\", \"args\": [\"tickers.BTCUSDT\"]}";
                session.sendMessage(new TextMessage(subscribeMessage));
            }

            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                BybitTickerDto ticker = objectMapper.readValue(message.getPayload(), BybitTickerDto.class);
                
                if (ticker.getData() != null && ticker.getData().getLastPrice() != null) {
                    double price = Double.parseDouble(ticker.getData().getLastPrice());
                    String key = "BY_" + ticker.getData().getSymbol();
                    priceManager.updatePrice(key, price);
                }
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                log.error("바이비트 소켓 에러 발생: {}", exception.getMessage());
            }
        }, BYBIT_WSS_URL);
    }
}
