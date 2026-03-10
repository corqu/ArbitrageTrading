package corque.gimpalarm.coin.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import corque.gimpalarm.coin.domain.CoinPrice;
import corque.gimpalarm.common.config.InfluxDbConfig;
import org.springframework.stereotype.Service;

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
     * 시세 데이터를 InfluxDB에 저장합니다.
     */
    public void savePrice(CoinPrice coinPrice) {
        WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
        writeApi.writeMeasurement(config.getBucket(), config.getOrg(), WritePrecision.MS, coinPrice);
    }

    /**
     * 시세 데이터 리스트를 한꺼번에 저장합니다. (Batch Write 추천)
     */
    public void savePrices(List<CoinPrice> coinPrices) {
        WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
        writeApi.writeMeasurements(config.getBucket(), config.getOrg(), WritePrecision.MS, coinPrices);
    }
}
