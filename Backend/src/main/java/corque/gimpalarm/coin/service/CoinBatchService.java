package corque.gimpalarm.coin.service;

import corque.gimpalarm.coin.domain.SupportedCoin;
import corque.gimpalarm.coin.repository.SupportedCoinRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CoinBatchService {

    private final SupportedCoinRepository coinRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    private final String UPBIT_MARKET_URL = "https://api.upbit.com/v1/market/all";
    private final String BITHUMB_MARKET_URL = "https://api.bithumb.com/public/ticker/ALL_KRW";
    private final String BINANCE_FUTURES_EXCHANGE_INFO_URL = "https://fapi.binance.com/fapi/v1/exchangeInfo";
    private final String BYBIT_FUTURES_MARKET_URL = "https://api.bybit.com/v5/market/instruments-info?category=linear";

    /**
     * 12시간마다 4개 거래소의 코인 목록을 갱신합니다.
     */
    @Scheduled(fixedRate = 43200000) // 6 hours
    public void updateSupportedCoins() {
        try {
            log.info("코인 목록 배치 갱신 시작...");

            // 1. 업비트 KRW 마켓 코인 가져오기
            Map[] upbitMarkets = restTemplate.getForObject(UPBIT_MARKET_URL, Map[].class);
            Set<String> upbitCoins = upbitMarkets == null ? Collections.emptySet() :
                    Arrays.stream(upbitMarkets)
                            .map(m -> (String) m.get("market"))
                            .filter(m -> m.startsWith("KRW-"))
                            .map(m -> m.replace("KRW-", "").toUpperCase())
                            .collect(Collectors.toSet());

            // 2. 빗썸 KRW 마켓 코인 가져오기
            Map bithumbRes = restTemplate.getForObject(BITHUMB_MARKET_URL, Map.class);
            Set<String> bithumbCoins = new HashSet<>();
            if (bithumbRes != null && "0000".equals(bithumbRes.get("status"))) {
                Map data = (Map) bithumbRes.get("data");
                for (Object key : data.keySet()) {
                    if (key instanceof String && !key.equals("date")) {
                        bithumbCoins.add(((String) key).toUpperCase());
                    }
                }
            }

            // 3. 바이낸스 선물 USDT 마켓 코인 가져오기
            Map binanceInfo = restTemplate.getForObject(BINANCE_FUTURES_EXCHANGE_INFO_URL, Map.class);
            Set<String> binanceCoins = new HashSet<>();
            if (binanceInfo != null && binanceInfo.containsKey("symbols")) {
                List<Map> symbols = (List<Map>) binanceInfo.get("symbols");
                binanceCoins = symbols.stream()
                        .filter(s -> "TRADING".equals(s.get("status")))
                        .filter(s -> ((String) s.get("symbol")).endsWith("USDT"))
                        .map(s -> ((String) s.get("symbol")).replace("USDT", "").toUpperCase())
                        .collect(Collectors.toSet());
            }

            // 4. 바이비트 선물 USDT 마켓 코인 가져오기
            Map bybitRes = restTemplate.getForObject(BYBIT_FUTURES_MARKET_URL, Map.class);
            Set<String> bybitCoins = new HashSet<>();
            if (bybitRes != null && (Integer) bybitRes.get("retCode") == 0) {
                Map result = (Map) bybitRes.get("result");
                List<Map> list = (List<Map>) result.get("list");
                bybitCoins = list.stream()
                        .filter(m -> "USDT".equals(m.get("quoteCoin")))
                        .map(m -> ((String) m.get("symbol")).replace("USDT", "").toUpperCase())
                        .collect(Collectors.toSet());
            }

            // 5. 모든 심볼 합집합 만들기
            Set<String> allSymbols = new HashSet<>();
            allSymbols.addAll(upbitCoins);
            allSymbols.addAll(bithumbCoins);
            allSymbols.addAll(binanceCoins);
            allSymbols.addAll(bybitCoins);

            int count = 0;
            for (String symbol : allSymbols) {
                boolean isUp = upbitCoins.contains(symbol);
                boolean isBt = bithumbCoins.contains(symbol);
                boolean isBn = binanceCoins.contains(symbol);
                boolean isBb = bybitCoins.contains(symbol);

                // 최소 한 개의 국내 거래소와 한 개의 해외 거래소에 있어야 김프 계산 가능
                if ((isUp || isBt) && (isBn || isBb)) {
                    SupportedCoin coin = coinRepository.findBySymbol(symbol)
                            .orElse(new SupportedCoin(symbol));
                    coin.updateExchanges(isUp, isBt, isBn, isBb);
                    coinRepository.save(coin);
                    count++;
                } else {
                    // 조건에 맞지 않으면 DB에서 삭제 (기존에 있었을 경우)
                    coinRepository.findBySymbol(symbol).ifPresent(coinRepository::delete);
                }
            }

            // 6. DB에만 있고 이제는 모든 거래소 목록에서 사라진 코인 제거
            List<SupportedCoin> allInDb = coinRepository.findAll();
            for (SupportedCoin dbCoin : allInDb) {
                if (!allSymbols.contains(dbCoin.getSymbol())) {
                    coinRepository.delete(dbCoin);
                }
            }

            log.info("코인 목록 배치 갱신 완료. 유효 코인(국내∩해외): {} 종", count);

        } catch (Exception e) {
            log.error("코인 목록 갱신 중 에러 발생: {}", e.getMessage());
            e.printStackTrace();
        }
    }
}
