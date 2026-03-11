package corque.gimpalarm.coin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import corque.gimpalarm.coin.dto.binance.BinanceCombinedResponse;
import corque.gimpalarm.coin.dto.binance.BinanceTickerDto;
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

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BinanceWebSocketService {

    private final String BINANCE_WSS_URL = "wss://stream.binance.com:9443/stream?streams=";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PriceManager priceManager;
    private final CoinConfig coinConfig;

    private String buildBinanceUrl() {
        String streams = coinConfig.getCoins().stream()
                .map(coin -> coin.toLowerCase() + "usdt@ticker")
                .collect(Collectors.joining("/"));

        return BINANCE_WSS_URL + streams;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        WebSocketClient client = new StandardWebSocketClient();
        String finalUrl = buildBinanceUrl();

        client.execute(new TextWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) {
                log.info("바이낸스 소켓 연결 성공: {}", finalUrl);
            }

            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                BinanceCombinedResponse response = objectMapper.readValue(message.getPayload(), BinanceCombinedResponse.class);

                BinanceTickerDto ticker = response.getData();
                if (ticker != null && ticker.getLastPrice() != null) {
                    double price = Double.parseDouble(ticker.getLastPrice());
                    String key = "BN_" + ticker.getSymbol().replace("USDT", "").toUpperCase();
                    priceManager.updatePrice(key, price);
                }
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                log.error("소켓 에러 발생");
            }
        }, finalUrl);
    }
}
