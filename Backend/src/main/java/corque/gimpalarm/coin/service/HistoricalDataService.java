package corque.gimpalarm.coin.service;

import corque.gimpalarm.coin.domain.KimchPremium;
import corque.gimpalarm.coin.domain.SupportedCoin;
import corque.gimpalarm.coin.repository.SupportedCoinRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class HistoricalDataService {

    private final SupportedCoinRepository coinRepository;
    private final CoinPriceService coinPriceService;
    private final RestTemplate restTemplate = new RestTemplate();

    private final String UPBIT_CANDLE_URL = "https://api.upbit.com/v1/candles/minutes/60?market=%s&to=%s&count=200";
    private final String BINANCE_CANDLE_URL = "https://fapi.binance.com/fapi/v1/klines?symbol=%sUSDT&interval=1h&limit=1000&endTime=%d";
    private final String BINANCE_FUNDING_URL = "https://fapi.binance.com/fapi/v1/fundingRate?symbol=%sUSDT&limit=1000";

    /**
     * 비동기로 6개월치 과거 데이터를 수집합니다.
     */
    @Async
    public void importSixMonthsHistory() {
        List<SupportedCoin> coins = coinRepository.findAll();
        log.info("과거 데이터 수집 시작 (대상: {} 종)", coins.size());

        // 1. 과거 환율 데이터 수집 (업비트 KRW-USDT 1시간봉 기준)
        Map<Long, Double> exchangeRates = fetchUpbitHistory("KRW-USDT", 180);
        log.info("과거 환율 데이터 수집 완료 ({} 포인트)", exchangeRates.size());

        for (SupportedCoin coin : coins) {
            try {
                String symbol = coin.getSymbol();
                log.info("{} 과거 데이터 분석 중...", symbol);

                // 2. 업비트 1시간봉 수집
                Map<Long, Double> upbitPrices = fetchUpbitHistory("KRW-" + symbol, 180);
                
                // 3. 바이낸스 1시간봉 수집
                Map<Long, Double> binancePrices = fetchBinanceHistory(symbol, 180);

                // 4. 바이낸스 과거 펀딩비 수집
                Map<Long, Double> fundingRates = fetchBinanceFundingHistory(symbol);

                // 5. 데이터 매칭 및 김프 계산
                List<KimchPremium> kimpHistory = new ArrayList<>();
                for (Long timestamp : upbitPrices.keySet()) {
                    Double upPrice = upbitPrices.get(timestamp);
                    Double bnPrice = binancePrices.get(timestamp);
                    Double rate = exchangeRates.get(timestamp);

                    if (bnPrice != null && rate != null) {
                        double ratio = ((upPrice / (bnPrice * rate)) - 1) * 100;
                        // 가장 가까운 시간대의 펀딩비 찾기 (8시간 단위이므로 근사치 사용)
                        Double fRate = findClosestFundingRate(fundingRates, timestamp);

                        KimchPremium kimp = KimchPremium.builder()
                                .symbol(symbol)
                                .domesticExchange("UPBIT")
                                .foreignExchange("BINANCE_FUTURES")
                                .ratio(ratio)
                                .fundingRate(fRate)
                                .tradeVolume(0.0) // 과거 데이터 거래대금은 일단 0으로 처리
                                .build();
                        kimp.setTime(Instant.ofEpochMilli(timestamp));
                        kimpHistory.add(kimp);
                    }
                }

                // 6. InfluxDB 저장
                if (!kimpHistory.isEmpty()) {
                    coinPriceService.saveKimchPremiums(kimpHistory);
                    log.info("{} 과거 데이터 {}건 저장 완료", symbol, kimpHistory.size());
                }

                // 거래소 API 제한 방지를 위해 짧은 휴식
                Thread.sleep(500);

            } catch (Exception e) {
                log.error("{} 데이터 수집 실패: {}", coin.getSymbol(), e.getMessage());
            }
        }
        log.info("모든 코인의 6개월 과거 데이터 수집 작업이 종료되었습니다.");
    }

    private Map<Long, Double> fetchUpbitHistory(String market, int days) {
        Map<Long, Double> data = new HashMap<>();
        Instant to = Instant.now();
        int iterations = (days * 24) / 200 + 1;

        for (int i = 0; i < iterations; i++) {
            String url = String.format(UPBIT_CANDLE_URL, market, to.toString());
            Map[] res = restTemplate.getForObject(url, Map[].class);
            if (res == null || res.length == 0) break;

            for (Map candle : res) {
                long ts = (long) candle.get("timestamp");
                double price = ((Number) candle.get("trade_price")).doubleValue();
                data.put(ts, price);
                to = Instant.ofEpochMilli(ts);
            }
            try { Thread.sleep(100); } catch (InterruptedException e) {}
        }
        return data;
    }

    private Map<Long, Double> fetchBinanceHistory(String symbol, int days) {
        Map<Long, Double> data = new HashMap<>();
        long endTime = System.currentTimeMillis();
        int iterations = 3; // 바이낸스는 한 번에 1000개 주므로 3~4번이면 6개월치

        for (int i = 0; i < iterations; i++) {
            String url = String.format(BINANCE_CANDLE_URL, symbol, endTime);
            List<List<Object>> res = restTemplate.getForObject(url, List.class);
            if (res == null || res.isEmpty()) break;

            for (List<Object> kline : res) {
                long ts = (long) kline.get(0);
                double price = Double.parseDouble((String) kline.get(4)); // 종가
                data.put(ts, price);
                endTime = ts;
            }
        }
        return data;
    }

    private Map<Long, Double> fetchBinanceFundingHistory(String symbol) {
        Map<Long, Double> data = new HashMap<>();
        String url = String.format(BINANCE_FUNDING_URL, symbol);
        List<Map<String, Object>> res = restTemplate.getForObject(url, List.class);
        if (res != null) {
            for (Map<String, Object> item : res) {
                long ts = (long) item.get("fundingTime");
                double rate = Double.parseDouble((String) item.get("fundingRate"));
                data.put(ts, rate);
            }
        }
        return data;
    }

    private Double findClosestFundingRate(Map<Long, Double> fundingRates, long targetTs) {
        if (fundingRates.isEmpty()) return 0.01 / 100; // 기본값 0.01%
        
        long closestTs = -1;
        long minDiff = Long.MAX_VALUE;
        
        for (Long ts : fundingRates.keySet()) {
            long diff = Math.abs(ts - targetTs);
            if (diff < minDiff) {
                minDiff = diff;
                closestTs = ts;
            }
        }
        // 펀딩비 발생 시점이 4시간 이내인 경우에만 적용 (보통 8시간 주기이므로)
        return (minDiff < 4 * 60 * 60 * 1000) ? fundingRates.get(closestTs) : 0.0;
    }
}
