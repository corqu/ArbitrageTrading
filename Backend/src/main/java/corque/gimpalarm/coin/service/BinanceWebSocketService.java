package corque.gimpalarm.coin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import corque.gimpalarm.coin.dto.BinanceTickerDto;
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
public class BinanceWebSocketService {

    private final String BINANCE_WSS_URL = "wss://stream.binance.com:9443/ws/btcusdt@ticker";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PriceManager priceManager;

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        WebSocketClient client = new StandardWebSocketClient();

        client.execute(new TextWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) {
                log.info("바이낸스 소켓 연결");
            }

            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                BinanceTickerDto ticker = objectMapper.readValue(message.getPayload(), BinanceTickerDto.class);
                double Price = Double.parseDouble(ticker.getLastPrice());
                String key = "BN_" + ticker.getSymbol();

                priceManager.updatePrice(key, Price);
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                log.error("소켓 에러 발생");
            }
        }, BINANCE_WSS_URL);
    }
}
