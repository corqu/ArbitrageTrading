package corque.gimpalarm.coin.dto.binance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BinanceFuturesResponse {
    private String stream;
    private BinanceFuturesTickerDto data;
}
