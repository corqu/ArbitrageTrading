package corque.gimpalarm.coin.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import corque.gimpalarm.coin.domain.KimchPremium;
import corque.gimpalarm.common.config.InfluxDbConfig;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class CoinPriceService {

    private final InfluxDBClient influxDBClient;
    private final InfluxDbConfig config;

    public CoinPriceService(InfluxDBClient influxDBClient, InfluxDbConfig config) {
        this.influxDBClient = influxDBClient;
        this.config = config;
    }

    /**
     * 김치 프리미엄 데이터를 InfluxDB에 저장합니다.
     */
    public void saveKimchPremiums(List<KimchPremium> kimpList) {
        WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
        writeApi.writeMeasurements(config.getBucket(), config.getOrg(), WritePrecision.MS, kimpList);
    }

    /**
     * InfluxDB에 김프 데이터가 이미 존재하는지 확인합니다.
     */
    public boolean hasHistoricalData() {
        String query = String.format(
            "from(bucket: \"%s\") |> range(start: -1y) |> filter(fn: (r) => r[\"_measurement\"] == \"kimp\") |> limit(n: 1)",
            config.getBucket()
        );
        List<KimchPremium> result = influxDBClient.getQueryApi().query(query, config.getOrg(), KimchPremium.class);
        return !result.isEmpty();
    }

    /**
     * 특정 코인의 최근 김프 히스토리를 조회합니다.
     */
    public List<KimchPremium> getKimpHistory(String symbol, String range, String domesticEx, String foreignEx) {
        String windowPeriod;
        if (range.equals("-6h")) windowPeriod = "5m";
        else if (range.equals("-24h")) windowPeriod = "10m";
        else if (range.equals("-7d")) windowPeriod = "1h";
        else if (range.equals("-30d")) windowPeriod = "4h";
        else windowPeriod = "10m";

        String query = String.format(
            "from(bucket: \"%s\") " +
            "|> range(start: %s) " +
            "|> filter(fn: (r) => r[\"_measurement\"] == \"kimp\") " +
            "|> filter(fn: (r) => r[\"symbol\"] == \"%s\") " +
            "|> filter(fn: (r) => r[\"domesticExchange\"] == \"%s\") " +
            "|> filter(fn: (r) => r[\"foreignExchange\"] == \"%s\") " +
            "|> aggregateWindow(every: %s, fn: mean, createEmpty: false) " +
            "|> pivot(rowKey:[\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\")",
            config.getBucket(), range, symbol.toUpperCase(), domesticEx, foreignEx, windowPeriod
        );

        return influxDBClient.getQueryApi().query(query, config.getOrg(), KimchPremium.class);
    }
}
