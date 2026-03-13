package corque.gimpalarm.coin.service;

import com.influxdb.client.InfluxDBClient;
import corque.gimpalarm.coin.domain.KimchPremium;
import corque.gimpalarm.coin.dto.arbitrage.BacktestResponse;
import corque.gimpalarm.common.config.InfluxDbConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ArbitrageBacktestService {

    private final InfluxDBClient influxDBClient;
    private final InfluxDbConfig config;

    private static final double TOTAL_FEE_ROUNDTRIP = 0.002; // 0.2%

    public BacktestResponse runBacktest(String symbol, double entryKimp, double exitKimp, String range) {
        String query = String.format(
            "from(bucket: \"%s\") " +
            "|> range(start: %s) " +
            "|> filter(fn: (r) => r[\"_measurement\"] == \"kimp\") " +
            "|> filter(fn: (r) => r[\"symbol\"] == \"%s\") " +
            "|> pivot(rowKey:[\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\")",
            config.getBucket(), range, symbol.toUpperCase()
        );

        List<KimchPremium> history = influxDBClient.getQueryApi().query(query, config.getOrg(), KimchPremium.class);

        if (history == null || history.size() < 2) {
            return BacktestResponse.builder().symbol(symbol).message("데이터 부족").build();
        }

        int totalTrades = 0;
        double accumulatedKimpProfit = 0.0;
        double accumulatedFundingProfit = 0.0;
        int totalFundingCount = 0;
        long totalHoldingDurationMs = 0;
        
        boolean isHolding = false;
        KimchPremium entryPoint = null;
        Instant lastFundingTime = null;

        for (KimchPremium current : history) {
            if (!isHolding) {
                if (current.getRatio() <= entryKimp) {
                    isHolding = true;
                    entryPoint = current;
                    lastFundingTime = current.getTime();
                }
            } else {
                // 1. 보유 중 펀딩비 누적 (매 8시간마다 펀딩이 발생한다고 가정)
                // 실제 바이낸스 정산 시각(01, 09, 17시)을 체크하면 더 정확하지만, 여기서는 단순화하여 8시간 간격으로 체크
                Duration timeFromLastFunding = Duration.between(lastFundingTime, current.getTime());
                if (timeFromLastFunding.toHours() >= 8) {
                    accumulatedFundingProfit += (current.getFundingRate() != null ? current.getFundingRate() : 0.0) * 100;
                    totalFundingCount++;
                    lastFundingTime = current.getTime();
                }

                // 2. 탈출 조건 체크
                if (current.getRatio() >= exitKimp) {
                    isHolding = false;
                    totalTrades++;
                    
                    // 김프 수익: (매도김프 - 매수김프) - 수수료
                    double tradeKimpProfit = (current.getRatio() - entryPoint.getRatio()) - (TOTAL_FEE_ROUNDTRIP * 100);
                    accumulatedKimpProfit += tradeKimpProfit;
                    
                    totalHoldingDurationMs += Duration.between(entryPoint.getTime(), current.getTime()).toMillis();
                }
            }
        }

        // 통계 환산
        double dataPeriodDays = (double) Duration.between(history.get(0).getTime(), history.get(history.size() - 1).getTime()).toMillis() / (1000.0 * 60 * 60 * 24);
        double avgHoldingDays = (totalTrades > 0) ? (totalHoldingDurationMs / (1000.0 * 60 * 60 * 24)) / totalTrades : 0;
        double totalReturn = accumulatedKimpProfit + accumulatedFundingProfit;

        return BacktestResponse.builder()
                .symbol(symbol)
                .entryThreshold(entryKimp)
                .exitThreshold(exitKimp)
                .totalTrades(totalTrades)
                .avgHoldingDays(avgHoldingDays)
                .kimpReturn(accumulatedKimpProfit)
                .fundingReturn(accumulatedFundingProfit)
                .totalReturn(totalReturn)
                .fundingCount(totalFundingCount)
                .winRate(totalTrades > 0 ? 100.0 : 0)
                .message(String.format("최근 %.1f일간의 원본 데이터를 분석했습니다.", dataPeriodDays))
                .build();
    }
}
