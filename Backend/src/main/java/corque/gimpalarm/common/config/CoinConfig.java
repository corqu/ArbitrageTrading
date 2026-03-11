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
    private int leverage;
    private double notificationThreshold;
    private Telegram telegram = new Telegram();

    @Getter
    @Setter
    public static class Telegram {
        private String botToken;
        private String chatId;
    }
}
