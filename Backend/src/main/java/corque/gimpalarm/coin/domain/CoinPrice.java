package corque.gimpalarm.coin.domain;

import com.influxdb.annotations.Column;
import com.influxdb.annotations.Measurement;

import java.time.Instant;

@Measurement(name = "coin_price")
public class CoinPrice {

    @Column(tag = true)
    private String exchange; // 거래소 (binance, upbit, bithumb, bybit)

    @Column(tag = true)
    private String symbol; // 심볼 (BTC, ETH)

    @Column(tag = true)
    private String market; // 마켓 (KRW, USDT)

    @Column
    private Double price; // 현재가

    @Column
    private Double volume; // 24시간 거래량

    @Column(timestamp = true)
    private Instant time; // 시간

    public CoinPrice(String exchange, String symbol, String market, Double price, Double volume) {
        this.exchange = exchange;
        this.symbol = symbol;
        this.market = market;
        this.price = price;
        this.volume = volume;
        this.time = Instant.now();
    }
}
