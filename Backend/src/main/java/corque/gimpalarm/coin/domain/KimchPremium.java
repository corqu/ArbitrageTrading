package corque.gimpalarm.coin.domain;

import com.influxdb.annotations.Column;
import com.influxdb.annotations.Measurement;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Measurement(name = "kimp")
public class KimchPremium {

    @Column(tag = true)
    private String symbol;

    @Column(tag = true)
    private String domesticExchange;

    @Column(tag = true)
    private String foreignExchange;

    @Column
    private Double standardRatio;

    @Column
    private Double entryRatio;

    @Column
    private Double exitRatio;

    @Column
    private Double fundingRate;

    @Column
    private Double tradeVolume;

    @Column(timestamp = true)
    private Instant time;

    @Builder
    public KimchPremium(String symbol, String domesticExchange, String foreignExchange,
                        Double standardRatio, Double entryRatio, Double exitRatio,
                        Double fundingRate, Double tradeVolume) {
        this.symbol = symbol;
        this.domesticExchange = domesticExchange;
        this.foreignExchange = foreignExchange;
        this.standardRatio = standardRatio;
        this.entryRatio = entryRatio;
        this.exitRatio = exitRatio;
        this.fundingRate = fundingRate;
        this.tradeVolume = tradeVolume;
        this.time = Instant.now();
    }
}
