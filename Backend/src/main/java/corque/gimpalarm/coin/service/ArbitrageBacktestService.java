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

    private static final double TICK_BUFFER = 0.15; // 0.15% 추가 여유가 있어야 실제 체결 가능하다고 가정 (틱 사이즈 고려)
    private static final double TOTAL_FEE_ROUNDTRIP = 0.004; // 0.4% (수수료 0.2% + 슬리피지/틱 손실 0.2%)

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
                // 진입: 목표 김프보다 버퍼만큼 더 떨어져야 실제 체결 (호가 경쟁 고려)
                if (current.getRatio() <= (entryKimp - TICK_BUFFER)) {
                    isHolding = true;
                    entryPoint = current;
                    lastFundingTime = current.getTime();
                }
            } else {
                // 1. 보유 중 펀딩비 누적
                Duration timeFromLastFunding = Duration.between(lastFundingTime, current.getTime());
                if (timeFromLastFunding.toHours() >= 8) {
                    accumulatedFundingProfit += (current.getFundingRate() != null ? current.getFundingRate() : 0.0) * 100;
                    totalFundingCount++;
                    lastFundingTime = current.getTime();
                }

                // 2. 탈출 조건 체크: 목표 김프보다 버퍼만큼 더 올라야 실제 체결 (호가 경쟁 고려)
                if (current.getRatio() >= (exitKimp + TICK_BUFFER)) {
                    isHolding = false;
                    totalTrades++;
                    
                    // 김프 수익: (매도김프 - 매수김프) - (수수료 및 슬리피지 적용)
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
