package corque.gimpalarm.coin.domain;

import com.influxdb.annotations.Column;
import com.influxdb.annotations.Measurement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Measurement(name = "usd_krw")
public class UsdKrwSnapshot {

    @Column(timestamp = true)
    private Instant time;

    @Column(name = "source", tag = true)
    private String source;

    @Column(name = "price")
    private Double price;
}
