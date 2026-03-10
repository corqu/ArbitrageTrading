package corque.gimpalarm.coin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BybitTickerDto {
    private String topic;
    private String type;
    private BybitData data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BybitData {
        private String symbol;
        private String lastPrice;
    }
}
