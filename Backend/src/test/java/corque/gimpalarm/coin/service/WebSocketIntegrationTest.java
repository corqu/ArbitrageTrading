package corque.gimpalarm.coin.service;

import corque.gimpalarm.coin.dto.PriceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 웹소켓 STOMP 연동 및 실시간 데이터 수신 테스트
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private PriceManager priceManager;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setup() {
        // SockJS 지원 클라이언트 설정
        List<Transport> transports = Collections.singletonList(new WebSocketTransport(new StandardWebSocketClient()));
        this.stompClient = new WebSocketStompClient(new SockJsClient(transports));
        this.stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @Test
    @DisplayName("서버의 김프 정보가 웹소켓(/topic/kimp)을 통해 구독자에게 전달되는지 테스트")
    void testReceiveKimpFromWebSocket() throws Exception {
        // 1. 테스트 데이터 세팅 (데이터가 있어야 계산되어 전송됨)
        priceManager.updateUsdKrw(1400.0);
        priceManager.updatePrice("UB_BTC", 140000.0);
        priceManager.updatePrice("BN_BTC", 100.0);

        // 2. STOMP 엔드포인트 접속
        String url = "ws://localhost:" + port + "/ws-stomp";
        BlockingQueue<Object> blockingQueue = new LinkedBlockingDeque<>();

        StompSession session = stompClient.connectAsync(url, new StompSessionHandlerAdapter() {
            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                exception.printStackTrace();
            }
        }).get(10, TimeUnit.SECONDS);

        // 3. /topic/kimp 구독 (서버가 0.5초마다 데이터를 전송하므로 수신 대기)
        session.subscribe("/topic/kimp", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return List.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                blockingQueue.offer(payload);
            }
        });

        // 4. 데이터 수신 대기 (최대 10초)
        Object receivedData = blockingQueue.poll(10, TimeUnit.SECONDS);

        // 5. 검증
        assertNotNull(receivedData, "웹소켓으로부터 데이터를 수신하지 못했습니다.");
        assertTrue(receivedData instanceof List, "수신된 데이터가 리스트 형식이 아닙니다.");
        
        List<?> kimpList = (List<?>) receivedData;
        System.out.println(">>> 수신된 데이터 개수: " + kimpList.size());
        System.out.println(">>> 수신 데이터 샘플: " + kimpList.get(0));
    }
}
