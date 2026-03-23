package corque.gimpalarm.coin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpbitTickerDto {
    private String code;

    @JsonProperty("trade_price")
    private double tradePrice;

    @JsonProperty("acc_trade_price_24h")
    private double accTradePrice24h;
}
