package corque.gimpalarm.coin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import corque.gimpalarm.coin.dto.PriceManager;
import corque.gimpalarm.coin.dto.UpbitTickerDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${kimp.coins}")
    private List<String> coins;

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        WebSocketClient client = new StandardWebSocketClient();

        client.execute(new AbstractWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                String coinJson = coins.stream()
                        .map(coin-> "\"" + "KRW-" + coin.toUpperCase() + "\"")
                        .collect(Collectors.joining(", "));
                // KRW-USDT를 추가하여 환율 대용으로 사용
                String subscribeMessage = String.format("[{\"ticket\":\"my_kimp_project\"},{\"type\":\"ticker\",\"codes\":[%s, \"KRW-USDT\"]}]"
                        , coinJson);
                session.sendMessage(new TextMessage(subscribeMessage));
                log.info("업비트 소켓 연결 (USDT 포함)");
            }

            @Override
            protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
                // 업비트는 기본적으로 바이너리 메시지를 보냄
                String payload = new String(message.getPayload().array(), StandardCharsets.UTF_8);
                UpbitTickerDto ticker = objectMapper.readValue(payload, UpbitTickerDto.class);

                if (ticker.getCode() != null) {
                    double price = ticker.getTradePrice();

                    if ("KRW-USDT".equals(ticker.getCode())) {
                        priceManager.updateUsdKrw(price); // 환율 업데이트
                    } else {
                        String key = "UB_" + ticker.getCode().split("-")[1].toUpperCase();
                        priceManager.updatePrice(key, price);
                    }
                }
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                log.error("업비트 소켓 에러 발생: {}", exception.getMessage());
            }
        }, UPBIT_WSS_URL);
    }
}
