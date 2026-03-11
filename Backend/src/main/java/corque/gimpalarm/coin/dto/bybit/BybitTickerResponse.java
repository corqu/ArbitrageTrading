package corque.gimpalarm.coin.dto.bybit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BybitTickerResponse {
    private String topic;
    private String type;
    private BybitTickerDto data;
}
