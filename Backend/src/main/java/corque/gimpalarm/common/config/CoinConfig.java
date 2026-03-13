package corque.gimpalarm.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "kimp")
@Getter
@Setter
public class CoinConfig {
    private List<String> coins;
    private int leverage = 3; // 기본값 3 설정
    private double notificationThreshold = 5.0; // 기본값 5% 설정
    private Telegram telegram = new Telegram();

    @Getter
    @Setter
    public static class Telegram {
        private String botToken;
        private String chatId;
    }
}
