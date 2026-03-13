package corque.gimpalarm.coin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import corque.gimpalarm.coin.dto.PriceManager;
import corque.gimpalarm.coin.dto.UpbitTickerDto;
import corque.gimpalarm.common.config.CoinConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
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
    private final CoinConfig coinConfig;
    private final CoinBatchService coinBatchService;
    private final BinanceFuturesWebSocketService binanceFuturesWebSocketService;

    @EventListener(ApplicationReadyEvent.class)
    @Order(1) // 코인 리스트 준비가 먼저 되어야 함
    public void init() {
        // 1. DB에서 코인 목록 로드 (없으면 최초 1회 API 호출)
        coinBatchService.syncConfigWithDb();
        if (coinConfig.getCoins() == null || coinConfig.getCoins().isEmpty()) {
            log.info("DB에 코인 목록이 없습니다. 최초 갱신을 시작합니다.");
            coinBatchService.updateSupportedCoins();
        }
        
        // 2. 업비트 소켓 연결
        connect();
        
        // 3. 바이낸스 선물 소켓 연결 (순차 실행)
        binanceFuturesWebSocketService.connect();
    }

    private void connect() {
        WebSocketClient client = new StandardWebSocketClient();

        client.execute(new AbstractWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                List<String> codes = coinConfig.getCoins().stream()
                        .map(coin -> "KRW-" + coin.toUpperCase())
                        .collect(Collectors.toList());
                codes.add("KRW-USDT"); // 환율용

                String coinJson = codes.stream()
                        .map(code -> "\"" + code + "\"")
                        .collect(Collectors.joining(", "));

                String subscribeMessage = String.format("[{\"ticket\":\"my_kimp_project\"},{\"type\":\"ticker\",\"codes\":[%s]}]", coinJson);
                session.sendMessage(new TextMessage(subscribeMessage));
                log.info("업비트 소켓 연결 및 구독 시작 ({} 종)", codes.size());
            }

            @Override
            protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
                String payload = new String(message.getPayload().array(), StandardCharsets.UTF_8);
                UpbitTickerDto ticker = objectMapper.readValue(payload, UpbitTickerDto.class);

                if (ticker.getCode() != null) {
                    double price = ticker.getTradePrice();
                    if ("KRW-USDT".equals(ticker.getCode())) {
                        priceManager.updateUsdKrw(price);
                    } else {
                        String symbol = ticker.getCode().split("-")[1].toUpperCase();
                        priceManager.updatePrice("UB_" + symbol, price);
                        priceManager.updateTradeVolume(symbol, ticker.getAccTradePrice24h()); // 거래대금 업데이트 추가
                    }
                }
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                log.error("업비트 소켓 에러: {}", exception.getMessage());
            }
        }, UPBIT_WSS_URL);
    }
}
