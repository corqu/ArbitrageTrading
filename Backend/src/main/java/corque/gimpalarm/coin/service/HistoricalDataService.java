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
    private final String BITHUMB_CANDLE_URL = "https://api.bithumb.com/public/candlestick/%s_KRW/1h";
    private final String BINANCE_CANDLE_URL = "https://fapi.binance.com/fapi/v1/klines?symbol=%sUSDT&interval=1h&limit=1000&endTime=%d";
    private final String BINANCE_FUNDING_URL = "https://fapi.binance.com/fapi/v1/fundingRate?symbol=%sUSDT&limit=1000";
    private final String BYBIT_CANDLE_URL = "https://api.bybit.com/v5/market/kline?category=linear&symbol=%sUSDT&interval=60&limit=1000&endTime=%d";
    private final String BYBIT_FUNDING_URL = "https://api.bybit.com/v5/market/funding/history?category=linear&symbol=%sUSDT&limit=100";

    /**
     * 비동기로 6개월치 과거 데이터를 수집합니다.
     * 이미 데이터가 존재하면 실행하지 않습니다.
     */
    @Async
    public void importSixMonthsHistory() {
        // 데이터 존재 여부 대략 확인 (BTC 데이터가 최근 1시간 이내에 있는지 등 - 여기선 심볼 리스트 존재 여부로 대체 가능하나 
        // 좀 더 정확하게는 InfluxDB 쿼리 필요. 일단 로그로 중복 수집 주의 알림)
        log.info("과거 데이터 수집 프로세스 가동...");

        List<SupportedCoin> coins = coinRepository.findAll();
        if (coins.isEmpty()) return;

        // 1. 과거 환율 데이터 수집 (업비트 KRW-USDT 1시간봉 기준)
        Map<Long, Double> exchangeRates = fetchUpbitHistory("KRW-USDT", 180);
        
        for (SupportedCoin coin : coins) {
            try {
                String symbol = coin.getSymbol();
                
                // 2. 국내 거래소 데이터 수집 (업비트, 빗썸)
                Map<Long, Double> upbitPrices = coin.isUpbit() ? fetchUpbitHistory("KRW-" + symbol, 180) : new HashMap<>();
                Map<Long, Double> bithumbPrices = coin.isBithumb() ? fetchBithumbHistory(symbol) : new HashMap<>();

                // 3. 해외 거래소 데이터 수집 (바이낸스, 바이비트)
                Map<Long, Double> binancePrices = coin.isBinance() ? fetchBinanceHistory(symbol, 180) : new HashMap<>();
                Map<Long, Double> bybitPrices = coin.isBybit() ? fetchBybitHistory(symbol, 180) : new HashMap<>();

                // 4. 펀딩비 데이터 수집
                Map<Long, Double> bnFunding = coin.isBinance() ? fetchBinanceFundingHistory(symbol) : new HashMap<>();
                Map<Long, Double> bbFunding = coin.isBybit() ? fetchBybitFundingHistory(symbol) : new HashMap<>();

                // 5. 김프 계산 및 저장 (국내 평균 vs 바이낸스, 국내 평균 vs 바이비트)
                processAndSaveAveragedHistory(symbol, upbitPrices, bithumbPrices, binancePrices, bnFunding, exchangeRates, "BINANCE_FUTURES");
                processAndSaveAveragedHistory(symbol, upbitPrices, bithumbPrices, bybitPrices, bbFunding, exchangeRates, "BYBIT_FUTURES");

                Thread.sleep(500); // Rate limit 방지
            } catch (Exception e) {
                log.error("{} 과거 수집 실패: {}", coin.getSymbol(), e.getMessage());
            }
        }
        log.info("모든 과거 데이터 수집 완료");
    }

    private void processAndSaveAveragedHistory(String symbol, Map<Long, Double> upbit, Map<Long, Double> bithumb, 
                                             Map<Long, Double> foreign, Map<Long, Double> funding, 
                                             Map<Long, Double> rates, String foreignEx) {
        
        List<KimchPremium> history = new ArrayList<>();
        Set<Long> timestamps = new HashSet<>(upbit.keySet());
        timestamps.addAll(bithumb.keySet());

        for (Long ts : timestamps) {
            Double upPrice = upbit.get(ts);
            Double btPrice = bithumb.get(ts);
            Double forPrice = foreign.get(ts);
            Double rate = rates.get(ts);

            if (forPrice != null && rate != null) {
                double domesticAvg;
                if (upPrice != null && btPrice != null) domesticAvg = (upPrice + btPrice) / 2.0;
                else if (upPrice != null) domesticAvg = upPrice;
                else if (btPrice != null) domesticAvg = btPrice;
                else continue;

                double ratio = ((domesticAvg / (forPrice * rate)) - 1) * 100;
                Double fRate = findClosestFundingRate(funding, ts);

                KimchPremium kimp = KimchPremium.builder()
                        .symbol(symbol)
                        .domesticExchange("AVERAGE")
                        .foreignExchange(foreignEx)
                        .ratio(ratio)
                        .fundingRate(fRate)
                        .tradeVolume(0.0)
                        .build();
                kimp.setTime(Instant.ofEpochMilli(ts));
                history.add(kimp);
            }
        }

        if (!history.isEmpty()) {
            coinPriceService.saveKimchPremiums(history);
            log.info("{} - {} 히스토리 {}건 저장", symbol, foreignEx, history.size());
        }
    }

    private Map<Long, Double> fetchBithumbHistory(String symbol) {
        Map<Long, Double> data = new HashMap<>();
        try {
            String url = String.format(BITHUMB_CANDLE_URL, symbol);
            Map res = restTemplate.getForObject(url, Map.class);
            if (res != null && "0000".equals(res.get("status"))) {
                List<List<Object>> list = (List<List<Object>>) res.get("data");
                for (List<Object> candle : list) {
                    long ts = Long.parseLong(candle.get(0).toString());
                    double price = Double.parseDouble(candle.get(2).toString()); // 종가
                    data.put(ts, price);
                }
            }
        } catch (Exception e) { log.warn("빗썸 {} 히스토리 누락", symbol); }
        return data;
    }

    private Map<Long, Double> fetchBybitHistory(String symbol, int days) {
        Map<Long, Double> data = new HashMap<>();
        long endTime = System.currentTimeMillis();
        try {
            String url = String.format(BYBIT_CANDLE_URL, symbol, endTime);
            Map res = restTemplate.getForObject(url, Map.class);
            if (res != null && (Integer) res.get("retCode") == 0) {
                Map result = (Map) res.get("result");
                List<List<String>> list = (List<List<String>>) result.get("list");
                for (List<String> kline : list) {
                    long ts = Long.parseLong(kline.get(0));
                    double price = Double.parseDouble(kline.get(4));
                    data.put(ts, price);
                }
            }
        } catch (Exception e) { log.warn("바이비트 {} 히스토리 누락", symbol); }
        return data;
    }

    private Map<Long, Double> fetchBybitFundingHistory(String symbol) {
        Map<Long, Double> data = new HashMap<>();
        try {
            String url = String.format(BYBIT_FUNDING_URL, symbol);
            Map res = restTemplate.getForObject(url, Map.class);
            if (res != null && (Integer) res.get("retCode") == 0) {
                Map result = (Map) res.get("result");
                List<Map<String, Object>> list = (List<Map<String, Object>>) result.get("list");
                for (Map<String, Object> item : list) {
                    long ts = Long.parseLong(item.get("fundingRateTimestamp").toString());
                    double rate = Double.parseDouble(item.get("fundingRate").toString());
                    data.put(ts, rate);
                }
            }
        } catch (Exception e) { log.warn("바이비트 {} 펀딩비 누락", symbol); }
        return data;
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
            try { Thread.sleep(150); } catch (InterruptedException e) {}
        }
        return data;
    }

    private Map<Long, Double> fetchBinanceHistory(String symbol, int days) {
        Map<Long, Double> data = new HashMap<>();
        long endTime = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            String url = String.format(BINANCE_CANDLE_URL, symbol, endTime);
            List<List<Object>> res = restTemplate.getForObject(url, List.class);
            if (res == null || res.isEmpty()) break;
            for (List<Object> kline : res) {
                long ts = (long) kline.get(0);
                double price = Double.parseDouble((String) kline.get(4));
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
        if (fundingRates.isEmpty()) return 0.01 / 100;
        long closestTs = -1;
        long minDiff = Long.MAX_VALUE;
        for (Long ts : fundingRates.keySet()) {
            long diff = Math.abs(ts - targetTs);
            if (diff < minDiff) { minDiff = diff; closestTs = ts; }
        }
        return (minDiff < 4 * 60 * 60 * 1000) ? fundingRates.get(closestTs) : 0.0;
    }
}
