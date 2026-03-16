package corque.gimpalarm.coin.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class KimpResponseDto {
    private String symbol;
    private String domesticExchange;
    private String foreignExchange;
    private Double ratio;
    private Double fundingRate;
    private Double adjustedApr;
    private Double liquidationPrice;
    private Double tradeVolume;
}
