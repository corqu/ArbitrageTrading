package corque.gimpalarm.coin.domain;

import com.influxdb.annotations.Column;
import com.influxdb.annotations.Measurement;

@Measurement(name = "kimp")
public class KimchPremium {

    @Column(tag = true)
    private String symbol;

    private Double ratio;
}
