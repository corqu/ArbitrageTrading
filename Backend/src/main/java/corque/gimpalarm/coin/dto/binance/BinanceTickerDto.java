package corque.gimpalarm.coin.dto.binance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BinanceTickerDto {
    @JsonProperty("s")
    private String symbol;

    @JsonProperty("c")
    private String lastPrice;
}
