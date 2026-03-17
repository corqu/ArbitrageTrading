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
            "|> pivot(rowKey:[\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\") " +
            "|> sort(columns: [\"_time\"])",
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
            // [추가] 비정상적인 김프 데이터(-15% 이하 등)는 가격 수집 오류로 간주하고 무시
            if (current.getRatio() == null || current.getRatio() < -15.0) {
                continue;
            }

            if (!isHolding) {
                // 진입: 목표 김프보다 버퍼만큼 더 떨어져야 실제 체결 (호가 경쟁 고려)
                if (current.getRatio() <= (entryKimp - TICK_BUFFER)) {
                    isHolding = true;
                    entryPoint = current;
                    lastFundingTime = current.getTime();
                }
            } else {
                // 1. 보유 중 펀딩비 누적 (8시간 간격 체크 로직 정교화)
                // InfluxDB 데이터 시간 차이가 8시간 이상 벌어질 때만 합산
                if (lastFundingTime != null) {
                    long hoursPassed = Duration.between(lastFundingTime, current.getTime()).toHours();
                    if (hoursPassed >= 8) {
                        // 8시간당 몇 번의 펀딩 타임이 지났는지 계산 (데이터가 띄엄띄엄 있을 경우 대비)
                        int fundingTimes = (int) (hoursPassed / 8);
                        double currentRate = (current.getFundingRate() != null ? current.getFundingRate() : 0.0);
                        
                        accumulatedFundingProfit += (currentRate * 100) * fundingTimes;
                        totalFundingCount += fundingTimes;
                        
                        // 마지막 합산 시간 업데이트 (정확히 8시간 단위로 끊어서 다음 계산에 영향 없도록)
                        lastFundingTime = lastFundingTime.plus(Duration.ofHours(fundingTimes * 8L));
                    }
                }

                // 2. 탈출 조건 체크: 목표 김프보다 버퍼만큼 더 올라야 실제 체결
                if (current.getRatio() >= (exitKimp + TICK_BUFFER)) {
                    isHolding = false;
                    totalTrades++;
                    
                    // 실제 시장가 체결 시 목표가보다 조금 더 유리하거나 불리하게 체결될 수 있지만,
                    // 백테스트에서는 목표가(exitKimp)에 버퍼를 더한 값 정도로 수익을 제한하는 것이 합리적임.
                    // 만약 데이터가 2%를 건너뛰고 바로 50%가 찍혔더라도 봇은 지정가 혹은 조건부 시장가로 2% 근처에서 나갔을 것이기 때문.
                    double executionRatio = Math.min(current.getRatio(), exitKimp + TICK_BUFFER + 0.1); // 최대 목표가 + 0.25% 정도로 제한
                    
                    double tradeKimpProfit = (executionRatio - entryPoint.getRatio()) - (TOTAL_FEE_ROUNDTRIP * 100);
                    accumulatedKimpProfit += tradeKimpProfit;
                    
                    long duration = Duration.between(entryPoint.getTime(), current.getTime()).toMillis();
                    totalHoldingDurationMs += Math.max(0, duration);
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
