package corque.gimpalarm.coin.dto;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Data
@Builder
public class KimpResponseDto {
    private String symbol;
    private String domesticExchange;
    private String foreignExchange;
    private Double standardRatio;
    private Double entryRatio;
    private Double exitRatio;
    // Debug fields to compare client-side displayed prices with server-side kimp inputs.
    private Double standardDomesticPrice;
    private Double entryDomesticPrice;
    private Double exitDomesticPrice;
    private Double foreignPrice;
    private Double usdKrw;
    private Double fundingRate;
    private Double tradeVolume;
    private Instant time;
}
