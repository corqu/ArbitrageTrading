package corque.gimpalarm.coin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import corque.gimpalarm.coin.dto.BithumbTickerDto;
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
public class BithumbWebSocketService {

    private final String BITHUMB_WSS_URL = "wss://pubwss.bithumb.com/pub/ws";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PriceManager priceManager;

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        WebSocketClient client = new StandardWebSocketClient();

        client.execute(new TextWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                log.info("빗썸 소켓 연결");
                String subscribeMessage = "{\"type\":\"ticker\", \"symbols\": [\"BTC_KRW\"], \"tickTypes\": [\"24H\"]}";
                session.sendMessage(new TextMessage(subscribeMessage));
            }

            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                BithumbTickerDto ticker = objectMapper.readValue(message.getPayload(), BithumbTickerDto.class);
                
                if (ticker.getContent() != null && ticker.getContent().getLastPrice() != null) {
                    double price = Double.parseDouble(ticker.getContent().getLastPrice());
                    String key = "BT_" + ticker.getContent().getSymbol();
                    priceManager.updatePrice(key, price);
                }
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                log.error("빗썸 소켓 에러 발생: {}", exception.getMessage());
            }
        }, BITHUMB_WSS_URL);
    }
}
