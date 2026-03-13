package corque.gimpalarm.coin.service;

import corque.gimpalarm.coin.domain.SupportedCoin;
import corque.gimpalarm.coin.repository.SupportedCoinRepository;
import corque.gimpalarm.common.config.CoinConfig;
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
    private final CoinConfig coinConfig;
    private final RestTemplate restTemplate = new RestTemplate();

    private final String UPBIT_MARKET_URL = "https://api.upbit.com/v1/market/all";
    private final String BINANCE_FUTURES_EXCHANGE_INFO_URL = "https://fapi.binance.com/fapi/v1/exchangeInfo";

    /**
     * 6시간마다 업비트와 바이낸스 선물의 교집합 코인 목록을 갱신합니다.
     */
    @Scheduled(fixedRate = 21600000) // 6 hours
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

            // 2. 바이낸스 선물 USDT 마켓 코인 가져오기
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

            // 3. 교집합 구하기
            Set<String> commonCoins = new HashSet<>(upbitCoins);
            commonCoins.retainAll(binanceCoins);

            if (commonCoins.isEmpty()) {
                log.warn("교집합 코인이 없습니다. 업데이트를 중단합니다.");
                return;
            }

            // 4. DB 업데이트 (새로운 코인 추가 및 기존 코인 갱신)
            for (String symbol : commonCoins) {
                Optional<SupportedCoin> existing = coinRepository.findBySymbol(symbol);
                if (existing.isPresent()) {
                    existing.get().setLastUpdated(LocalDateTime.now());
                    coinRepository.save(existing.get());
                } else {
                    coinRepository.save(new SupportedCoin(symbol));
                }
            }

            // 5. DB에만 있고 교집합에는 없는 코인 삭제 (상장 폐지 등 대응)
            List<SupportedCoin> allInDb = coinRepository.findAll();
            for (SupportedCoin dbCoin : allInDb) {
                if (!commonCoins.contains(dbCoin.getSymbol())) {
                    coinRepository.delete(dbCoin);
                    log.info("상장 폐지 또는 조건 미달 코인 제거: {}", dbCoin.getSymbol());
                }
            }

            // 6. 실시간 구독 리스트 동기화
            syncConfigWithDb();
            
            log.info("코인 목록 배치 갱신 완료. 현재 지원 코인: {} 종", commonCoins.size());

        } catch (Exception e) {
            log.error("코인 목록 갱신 중 에러 발생: {}", e.getMessage());
        }
    }

    /**
     * DB에서 코인 목록을 읽어 CoinConfig에 설정합니다.
     */
    public void syncConfigWithDb() {
        List<String> coins = coinRepository.findAll().stream()
                .map(SupportedCoin::getSymbol)
                .map(String::toLowerCase)
                .collect(Collectors.toList());
        
        if (!coins.isEmpty()) {
            coinConfig.setCoins(coins);
        }
    }
}
