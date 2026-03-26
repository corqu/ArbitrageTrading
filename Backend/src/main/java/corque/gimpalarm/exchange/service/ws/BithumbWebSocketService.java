package corque.gimpalarm.exchange.service.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import corque.gimpalarm.coin.dto.BithumbTickerDto;
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
public class BithumbWebSocketService {

    private final String BITHUMB_WSS_URL = "wss://pubwss.bithumb.com/pub/ws";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PriceManager priceManager;
    private final SupportedCoinRepository coinRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        WebSocketClient client = new StandardWebSocketClient();

        client.execute(new TextWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                // 빗썸에 상장되어 있으면서 해외 거래소 짝이 있는 코인만 필터링
                List<String> symbols = coinRepository.findAll().stream()
                        .filter(c -> c.isBithumb())
                        .map(c -> "\"" + c.getSymbol().toUpperCase() + "_KRW\"")
                        .collect(Collectors.toList());

                if (symbols.isEmpty()) {
                    log.warn("빗썸 구독 대상 코인이 없습니다.");
                    return;
                }

                // 모든 코인을 하나의 리스트로 묶어서 단 한 번의 메시지로 전송 (구독 덮어쓰기 방지)
                String allSymbols = String.join(", ", symbols);
                String subscribeMessage = String.format("{\"type\":\"ticker\", \"symbols\": [%s], \"tickTypes\": [\"24H\"]}", allSymbols);
                
                session.sendMessage(new TextMessage(subscribeMessage));
                log.info("빗썸 소켓 구독 완료: 총 {}종 (한 번의 메시지로 전송)", symbols.size());
            }

            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                try {
                    BithumbTickerDto ticker = objectMapper.readValue(message.getPayload(), BithumbTickerDto.class);
                    
                    if (ticker.getContent() != null && ticker.getContent().getClosePrice() != null) {
                        double price = Double.parseDouble(ticker.getContent().getClosePrice());

                        if (price <= 0) return;

                        String symbol = ticker.getContent().getSymbol().split("_")[0];
                        String key = "BT_" + symbol;
                        priceManager.updatePrice(key, price);
                        
                        if (ticker.getContent().getValue() != null) {
                            double volume = Double.parseDouble(ticker.getContent().getValue());
                            priceManager.updateTradeVolume(key, volume);
                        }
                    }
                } catch (Exception e) {
                }
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                log.error("빗썸 소켓 에러 발생: {}", exception.getMessage());
            }
        }, BITHUMB_WSS_URL);
    }
}
