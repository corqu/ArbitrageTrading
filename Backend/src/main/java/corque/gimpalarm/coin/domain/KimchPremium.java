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
    private String domesticExchange; // 업비트, 빗썸

    @Column(tag = true)
    private String foreignExchange; // 바이낸스, 바이비트

    @Column
    private Double ratio; // 김프 비율 (%)

    @Column
    private Double domesticPrice; // 국내 가격

    @Column
    private Double foreignPrice; // 해외 가격 (USDT)

    @Column(timestamp = true)
    private Instant time;

    @Builder
    public KimchPremium(String symbol, String domesticExchange, String foreignExchange, Double ratio, Double domesticPrice, Double foreignPrice) {
        this.symbol = symbol;
        this.domesticExchange = domesticExchange;
        this.foreignExchange = foreignExchange;
        this.ratio = ratio;
        this.domesticPrice = domesticPrice;
        this.foreignPrice = foreignPrice;
        this.time = Instant.now();
    }
}
