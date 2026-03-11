package corque.gimpalarm.coin.dto.bybit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BybitTickerDto {
        private String symbol;
        private String lastPrice;
}
