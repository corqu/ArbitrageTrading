package corque.gimpalarm.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 메시지 브로커가 해당 경로로 시작하는 대상에게 메시지를 전달하도록 설정
        config.enableSimpleBroker("/topic");
        // 클라이언트에서 메시지를 보낼 때 사용하는 경로 접두사 (현재는 서버 -> 클라이언트 위주)
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 프론트엔드에서 WebSocket에 접속할 엔드포인트 설정
        registry.addEndpoint("/ws-stomp")
                .setAllowedOriginPatterns("*") // CORS 허용 (테스트용)
                .withSockJS(); // SockJS 지원
    }
}
