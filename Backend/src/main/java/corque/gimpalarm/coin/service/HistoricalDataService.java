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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * 6개월치 시세 데이터를 비동기로 수집해 InfluxDB에 저장한다.
     * 평균 국내가(업비트/빗썸)와 해외 선물 가격을 기준으로 과거 김프를 계산한다.
     */
    @Async
    public void importSixMonthsHistory() {
        log.info("6개월치 과거 시세 수집을 시작합니다.");

        List<SupportedCoin> coins = coinRepository.findAll();
        if (coins.isEmpty()) {
            return;
        }

        // 환율 기준은 Upbit KRW-USDT 1시간 봉을 사용한다.
        Map<Long, Double> exchangeRates = fetchUpbitHistory("KRW-USDT", 180);

        for (SupportedCoin coin : coins) {
            try {
                String symbol = coin.getSymbol();

                // 국내 현물 시세
                Map<Long, Double> upbitPrices = coin.isUpbit() ? fetchUpbitHistory("KRW-" + symbol, 180) : new HashMap<>();
                Map<Long, Double> bithumbPrices = coin.isBithumb() ? fetchBithumbHistory(symbol) : new HashMap<>();

                // 해외 선물 시세
                Map<Long, Double> binancePrices = coin.isBinance() ? fetchBinanceHistory(symbol, 180) : new HashMap<>();
                Map<Long, Double> bybitPrices = coin.isBybit() ? fetchBybitHistory(symbol, 180) : new HashMap<>();

                // 펀딩비 이력
                Map<Long, Double> bnFunding = coin.isBinance() ? fetchBinanceFundingHistory(symbol) : new HashMap<>();
                Map<Long, Double> bbFunding = coin.isBybit() ? fetchBybitFundingHistory(symbol) : new HashMap<>();

                processAndSaveExchangeHistory(symbol, "UPBIT", upbitPrices, binancePrices, bnFunding, exchangeRates, "BINANCE_FUTURES");
                processAndSaveExchangeHistory(symbol, "BITHUMB", bithumbPrices, binancePrices, bnFunding, exchangeRates, "BINANCE_FUTURES");
                processAndSaveExchangeHistory(symbol, "UPBIT", upbitPrices, bybitPrices, bbFunding, exchangeRates, "BYBIT_FUTURES");
                processAndSaveExchangeHistory(symbol, "BITHUMB", bithumbPrices, bybitPrices, bbFunding, exchangeRates, "BYBIT_FUTURES");

                Thread.sleep(500); // 외부 거래소 rate limit 완화
            } catch (Exception e) {
                log.error("{} 과거 데이터 수집 실패: {}", coin.getSymbol(), e.getMessage());
            }
        }

        log.info("6개월치 과거 시세 수집이 완료되었습니다.");
    }

    private void processAndSaveExchangeHistory(String symbol, String domesticExchange, Map<Long, Double> domesticPrices,
                                               Map<Long, Double> foreign, Map<Long, Double> funding,
                                               Map<Long, Double> rates, String foreignEx) {

        List<KimchPremium> history = new ArrayList<>();
        Set<Long> timestamps = new HashSet<>(domesticPrices.keySet());

        for (Long ts : timestamps) {
            Double domesticPrice = domesticPrices.get(ts);
            Double forPrice = foreign.get(ts);
            Double rate = rates.get(ts);

            if (domesticPrice == null || forPrice == null || rate == null) continue;

            double ratio = ((domesticPrice / (forPrice * rate)) - 1) * 100;
            Double fRate = findClosestFundingRate(funding, ts);

            KimchPremium kimp = KimchPremium.builder()
                    .symbol(symbol)
                    .domesticExchange(domesticExchange)
                    .foreignExchange(foreignEx)
                    .standardRatio(ratio)
                    .entryRatio(ratio)
                    .exitRatio(ratio)
                    .fundingRate(fRate)
                    .tradeVolume(0.0)
                    .build();
            kimp.setTime(Instant.ofEpochMilli(ts));
            history.add(kimp);
        }

        if (!history.isEmpty()) {
            coinPriceService.saveKimchPremiums(history);
            log.info("{} - {} - {} 과거 데이터 {}건 저장", symbol, domesticExchange, foreignEx, history.size());
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
                    double price = Double.parseDouble(candle.get(2).toString());
                    data.put(ts, price);
                }
            }
        } catch (Exception e) {
            log.warn("빗썸 {} 시세 조회 실패", symbol);
        }
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
        } catch (Exception e) {
            log.warn("바이비트 {} 시세 조회 실패", symbol);
        }
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
        } catch (Exception e) {
            log.warn("바이비트 {} 펀딩비 조회 실패", symbol);
        }
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
            try {
                Thread.sleep(150);
            } catch (InterruptedException ignored) {
            }
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
            if (diff < minDiff) {
                minDiff = diff;
                closestTs = ts;
            }
        }
        return (minDiff < 4 * 60 * 60 * 1000) ? fundingRates.get(closestTs) : 0.0;
    }
}
