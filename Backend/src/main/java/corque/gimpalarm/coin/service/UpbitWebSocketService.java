package corque.gimpalarm.coin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import corque.gimpalarm.coin.dto.PriceManager;
import corque.gimpalarm.coin.dto.UpbitTickerDto;
import corque.gimpalarm.coin.repository.SupportedCoinRepository;
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
    private final SupportedCoinRepository coinRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        log.info("실시간 소켓 연결을 시작합니다.");
        
        // 1. 업비트 소켓 연결
        connect();
        
        // 2. 바이낸스 선물 소켓 연결
        binanceFuturesWebSocketService.connect();
    }

    private void connect() {
        WebSocketClient client = new StandardWebSocketClient();

        client.execute(new AbstractWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                // 업비트에 상장된 코인만 필터링
                List<String> codes = coinRepository.findAll().stream()
                        .filter(c -> c.isUpbit())
                        .map(c -> "KRW-" + c.getSymbol().toUpperCase())
                        .collect(Collectors.toList());
                
                codes.add("KRW-USDT"); // 환율용

                String coinJson = codes.stream()
                        .map(code -> "\"" + code + "\"")
                        .collect(Collectors.joining(", "));

                String subscribeMessage = String.format("[{\"ticket\":\"my_kimp_project\"},{\"type\":\"ticker\",\"codes\":[%s]}]", coinJson);
                session.sendMessage(new TextMessage(subscribeMessage));
                log.info("업비트 소켓 구독: {} 종", codes.size());
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
                        priceManager.updateTradeVolume("UB_" + symbol, ticker.getAccTradePrice24h()); // 거래대금 업데이트 추가
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
