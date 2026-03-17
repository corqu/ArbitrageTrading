package corque.gimpalarm.coin.dto;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Data
@Builder
public class KimpResponseDto {
    private String symbol;
    private String domesticExchange;
    private String foreignExchange;
    private double ratio;
    private Double fundingRate;
    private Double tradeVolume;
}

