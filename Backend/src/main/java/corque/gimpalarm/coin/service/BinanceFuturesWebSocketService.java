package corque.gimpalarm.coin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import corque.gimpalarm.coin.dto.binance.BinanceFuturesResponse;
import corque.gimpalarm.coin.dto.binance.BinanceFuturesTickerDto;
import corque.gimpalarm.coin.dto.PriceManager;
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

import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BinanceFuturesWebSocketService {

    // 바이낸스 선물 웹소켓 베이스 URL (Combined Stream 형식)
    private final String BINANCE_FUTURES_WSS_URL = "wss://fstream.binance.com/stream?streams=";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PriceManager priceManager;
    private final CoinConfig coinConfig;

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        WebSocketClient client = new StandardWebSocketClient();
        
        // 구독할 스트림: <symbol>@markPrice (마크 가격 및 펀딩비)
        String streams = coinConfig.getCoins().stream()
                .map(coin -> coin.toLowerCase() + "usdt@markPrice")
                .collect(Collectors.joining("/"));

        String finalUrl = BINANCE_FUTURES_WSS_URL + streams;

        client.execute(new TextWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) {
                log.info("바이낸스 선물 소켓 연결 성공: {}", finalUrl);
            }

            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                // Combined Stream 데이터 파싱 (data 필드에 실제 내용이 있음)
                BinanceFuturesResponse response = objectMapper.readValue(message.getPayload(), BinanceFuturesResponse.class);
                BinanceFuturesTickerDto ticker = response.getData();

                if (ticker != null && ticker.getSymbol() != null) {
                    String coinName = ticker.getSymbol().replace("USDT", "").toUpperCase();
                    
                    // 1. 선물 마크 가격 저장 (BN_F_ 접두사)
                    double markPrice = Double.parseDouble(ticker.getMarkPrice());
                    priceManager.updatePrice("BN_F_" + coinName, markPrice);
                    
                    // 2. 펀딩비 정보 저장 (별도 맵)
                    double fundingRate = Double.parseDouble(ticker.getFundingRate());
                    priceManager.updateFundingRate(coinName, fundingRate, ticker.getNextFundingTime());
                }
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {
                log.error("바이낸스 선물 소켓 에러: {}", exception.getMessage());
            }
        }, finalUrl);
    }
}
