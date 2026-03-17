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
    private String symbol; // BTC, ETH 등

    @Column(tag = true)
    private String domesticExchange; // UPBIT

    @Column(tag = true)
    private String foreignExchange; // BINANCE_SPOT, BINANCE_FUTURES

    @Column
    private Double ratio; // 계산된 김프 비율 (%)

    @Column
    private Double fundingRate; // 현재 실시간 펀딩비 (선물일 경우에만 존재)

    @Column
    private Double tradeVolume; // 업비트 24시간 누적 거래대금 (KRW)

    @Column(timestamp = true)
    private Instant time;

    @Builder
    public KimchPremium(String symbol, String domesticExchange, String foreignExchange, 
                       Double ratio, Double fundingRate, Double tradeVolume) {
        this.symbol = symbol;
        this.domesticExchange = domesticExchange;
        this.foreignExchange = foreignExchange;
        this.ratio = ratio;
        this.fundingRate = fundingRate;
        this.tradeVolume = tradeVolume;
        this.time = Instant.now();
    }
}
