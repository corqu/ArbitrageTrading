package corque.gimpalarm.coin.service;

import corque.gimpalarm.common.config.CoinConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramService {

    private final CoinConfig coinConfig;
    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, Long> lastAlertTimes = new ConcurrentHashMap<>(); // 알림 중복 방지 (쿨타임)

    public void sendMessage(String message) {
        String botToken = coinConfig.getTelegram().getBotToken();
        String chatId = coinConfig.getTelegram().getChatId();

        if (botToken == null || chatId == null || botToken.contains("YOUR_BOT_TOKEN")) {
            log.warn("텔레그램 설정이 올바르지 않아 메시지를 보낼 수 없습니다.");
            return;
        }

        String url = String.format("https://api.telegram.org/bot%s/sendMessage?chat_id=%s&text=%s", 
                                    botToken, chatId, message);

        try {
            restTemplate.getForObject(url, String.class);
            log.info("텔레그램 알림 전송 성공: {}", message);
        } catch (Exception e) {
            log.error("텔레그램 전송 실패: {}", e.getMessage());
        }
    }

    /**
     * 특정 코인에 대해 알림을 보낼지 결정합니다. (1시간 쿨타임)
     */
    public boolean shouldSendAlert(String coinSymbol) {
        long now = System.currentTimeMillis();
        long lastTime = lastAlertTimes.getOrDefault(coinSymbol, 0L);
        
        // 1시간(3600,000ms) 지나야 다시 알림
        if (now - lastTime > 3600000) {
            lastAlertTimes.put(coinSymbol, now);
            return true;
        }
        return false;
    }
}
