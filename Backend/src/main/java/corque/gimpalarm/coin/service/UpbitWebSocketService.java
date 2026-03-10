package corque.gimpalarm.coin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import corque.gimpalarm.coin.dto.PriceManager;
import corque.gimpalarm.coin.dto.UpbitTickerDto;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class UpbitWebSocketService {

    private final String UPBIT_WSS_URL = "wss://api.upbit.com/websocket/v1";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PriceManager priceManager;

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        WebSocketClient client = new StandardWebSocketClient();

        client.execute(new AbstractWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                log.info("업비트 소켓 연결");
                // 업비트는 리스트 형태의 구독 메시지를 요구함
                String subscribeMessage = "[{\"ticket\":\"test\"},{\"type\":\"ticker\",\"codes\":[\"KRW-BTC\"]}]";
                session.sendMessage(new TextMessage(subscribeMessage));
            }

            @Override
            protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
                // 업비트는 기본적으로 바이너리 메시지를 보냄
                String payload = new String(message.getPayload().array(), StandardCharsets.UTF_8);
                UpbitTickerDto ticker = objectMapper.readValue(payload, UpbitTickerDto.class);
                
                if (ticker.getCode() != null) {
                    double price = ticker.getTradePrice();
                    String key = "UB_" + ticker.getCode();
                    priceManager.updatePrice(key, price);
                }
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                log.error("업비트 소켓 에러 발생: {}", exception.getMessage());
            }
        }, UPBIT_WSS_URL);
    }
}
