package corque.gimpalarm.coin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BithumbTickerDto {

    private BithumbContent content;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BithumbContent {
        private String symbol;
        private String closePrice;
        private String value; // 24시 거래금액
    }
}
