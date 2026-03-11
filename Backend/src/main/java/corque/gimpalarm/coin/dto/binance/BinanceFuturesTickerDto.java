package corque.gimpalarm.coin.dto.binance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BinanceFuturesTickerDto {
    @JsonProperty("s")
    private String symbol;      // 심볼 (예: BTCUSDT)

    @JsonProperty("p")
    private String markPrice;   // 마크 가격 (선물 현재가)

    @JsonProperty("r")
    private String fundingRate; // 실시간 펀딩비 비율 (0.0001 = 0.01%)

    @JsonProperty("T")
    private long nextFundingTime; // 다음 펀딩비 지급 시간
}
