package corque.gimpalarm.coin.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import corque.gimpalarm.coin.domain.UsdKrwSnapshot;
import corque.gimpalarm.common.config.InfluxDbConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UsdKrwCacheService {

    private final InfluxDBClient influxDBClient;
    private final InfluxDbConfig config;

    public void save(double price, String source) {
        if (price <= 0) {
            return;
        }

        try {
            WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
            writeApi.writeMeasurement(
                    config.getBucket(),
                    config.getOrg(),
                    WritePrecision.MS,
                    UsdKrwSnapshot.builder()
                            .time(Instant.now())
                            .source(source)
                            .price(price)
                            .build()
            );
        } catch (Exception e) {
            log.warn("Failed to persist KRW-USDT price to InfluxDB: {}", e.getMessage());
        }
    }

    public Optional<Double> getLatest() {
        String query = String.format(
                "from(bucket: \"%s\") " +
                "|> range(start: -30d) " +
                "|> filter(fn: (r) => r[\"_measurement\"] == \"usd_krw\") " +
                "|> filter(fn: (r) => r[\"_field\"] == \"price\") " +
                "|> last()",
                config.getBucket()
        );

        try {
            List<UsdKrwSnapshot> result = influxDBClient.getQueryApi().query(query, config.getOrg(), UsdKrwSnapshot.class);
            if (result.isEmpty()) {
                return Optional.empty();
            }

            return Optional.ofNullable(result.get(0).getPrice());
        } catch (Exception e) {
            log.warn("Failed to restore KRW-USDT price from InfluxDB: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
