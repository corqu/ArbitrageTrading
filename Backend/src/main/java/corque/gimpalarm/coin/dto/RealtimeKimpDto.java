package corque.gimpalarm.coin.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RealtimeKimpDto {
    private String symbol;
    private Double standardRatio;
    private Double entryRatio;
    private Double exitRatio;
    private Double fundingRate;
    private Double tradeVolume;
}
