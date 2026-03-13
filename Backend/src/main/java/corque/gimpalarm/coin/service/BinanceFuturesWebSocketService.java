package corque.gimpalarm.coin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import corque.gimpalarm.coin.dto.binance.BinanceFuturesResponse;
import corque.gimpalarm.coin.dto.binance.BinanceFuturesTickerDto;
import corque.gimpalarm.coin.dto.PriceManager;
import corque.gimpalarm.common.config.CoinConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class BinanceFuturesWebSocketService {

    private final String BINANCE_FUTURES_WSS_URL = "wss://fstream.binance.com/ws"; // Base URL로 변경
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PriceManager priceManager;
    private final CoinConfig coinConfig;

    public void connect() {
        if (coinConfig.getCoins() == null || coinConfig.getCoins().isEmpty()) {
            log.warn("바이낸스 선물 구독 리스트가 비어있습니다.");
            return;
        }

        WebSocketClient client = new StandardWebSocketClient();

        client.execute(new TextWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                // URL 방식 대신 SUBSCRIBE 메시지 방식으로 변경 (대량 구독 가능)
                List<String> params = coinConfig.getCoins().stream()
                        .map(coin -> coin.toLowerCase() + "usdt@markPrice")
                        .collect(Collectors.toList());

                // 바이낸스는 한 번에 최대 200개까지 구독 가능
                String subscribeMessage = String.format(
                    "{\"method\": \"SUBSCRIBE\", \"params\": [%s], \"id\": 1}",
                    params.stream().map(p -> "\"" + p + "\"").collect(Collectors.joining(","))
                );

                session.sendMessage(new TextMessage(subscribeMessage));
                log.info("바이낸스 선물 소켓 연결 및 {} 종 구독 메시지 전송", params.size());
            }

            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                String payload = message.getPayload();
                if (payload.contains("result")) {
                    log.info("바이낸스 구독 응답 수신: {}", payload);
                    return;
                }

                try {
                    BinanceFuturesTickerDto ticker = objectMapper.readValue(payload, BinanceFuturesTickerDto.class);

                    if (ticker != null && ticker.getSymbol() != null) {
                        String coinName = ticker.getSymbol().replace("USDT", "").toUpperCase();
                        
                        // 디버그 로그: 어떤 코인이 들어오고 있는지 확인 (너무 많으면 주석 처리 예정)
                        // log.debug("바이낸스 수신: {}", coinName);
                        
                        double markPrice = Double.parseDouble(ticker.getMarkPrice());
                        double fundingRate = Double.parseDouble(ticker.getFundingRate());
                        
                        priceManager.updatePrice("BN_F_" + coinName, markPrice);
                        priceManager.updateFundingRate(coinName, fundingRate, ticker.getNextFundingTime());
                    }
                } catch (Exception e) {
                    log.error("바이낸스 파싱 에러 (데이터: {}): {}", payload, e.getMessage());
                }
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {
                log.error("바이낸스 선물 소켓 에러: {}", exception.getMessage());
            }
        }, BINANCE_FUTURES_WSS_URL);
    }
}
