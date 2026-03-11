package corque.gimpalarm.coin.service;

import corque.gimpalarm.coin.domain.KimchPremium;
import corque.gimpalarm.coin.dto.PriceManager;
import corque.gimpalarm.common.config.CoinConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class KimpService {

    private final PriceManager priceManager;
    private final CoinPriceService coinPriceService;
    private final SimpMessagingTemplate messagingTemplate;
    private final CoinConfig coinConfig;

    /**
     * 1분마다 김치 프리미엄을 계산하여 InfluxDB에 저장합니다. (배치 저장)
     */
    @Scheduled(fixedRate = 60000)
    public void calculateAndSaveKimp() {
        List<KimchPremium> kimpList = calculateAllKimp();
        if (!kimpList.isEmpty()) {
            coinPriceService.saveKimchPremiums(kimpList);
            log.info("1분 단위 김프 배치 저장 완료 ({} 건)", kimpList.size());
        }
    }

    /**
     * 실시간 김프 정보를 프론트엔드로 전송합니다. (0.5초 주기)
     */
    @Scheduled(fixedRate = 500)
    public void sendRealTimeKimp() {
        List<KimchPremium> kimpList = calculateAllKimp();
        if (!kimpList.isEmpty()) {
            // /topic/kimp 경로를 구독 중인 모든 클라이언트에게 전송
            messagingTemplate.convertAndSend("/topic/kimp", kimpList);
        }
    }

    /**
     * 현재 메모리(PriceManager)에 저장된 가격들을 기반으로 모든 김프 조합을 계산합니다.
     */
    private List<KimchPremium> calculateAllKimp() {
        Double usdKrw = priceManager.getCurrentUsdKrw();
        List<KimchPremium> kimpList = new ArrayList<>();

        if (usdKrw == null || usdKrw == 0) {
            return kimpList;
        }

        for (String coin : coinConfig.getCoins()) {
            String symbol = coin.toUpperCase();
            
            Double upbitPrice = priceManager.getPrice("UB_" + symbol);
            Double bithumbPrice = priceManager.getPrice("BT_" + symbol);
            Double binancePrice = priceManager.getPrice("BN_" + symbol);
            Double bybitPrice = priceManager.getPrice("BY_" + symbol);

            if (upbitPrice != null && binancePrice != null) {
                kimpList.add(calculateKimp(symbol, "UPBIT", "BINANCE", upbitPrice, binancePrice, usdKrw));
            }
            if (bithumbPrice != null && binancePrice != null) {
                kimpList.add(calculateKimp(symbol, "BITHUMB", "BINANCE", bithumbPrice, binancePrice, usdKrw));
            }
            if (upbitPrice != null && bybitPrice != null) {
                kimpList.add(calculateKimp(symbol, "UPBIT", "BYBIT", upbitPrice, bybitPrice, usdKrw));
            }
            if (bithumbPrice != null && bybitPrice != null) {
                kimpList.add(calculateKimp(symbol, "BITHUMB", "BYBIT", bithumbPrice, bybitPrice, usdKrw));
            }
        }
        return kimpList;
    }

    private KimchPremium calculateKimp(String symbol, String domesticEx, String foreignEx, 
                                      Double domesticPrice, Double foreignPrice, Double usdKrw) {
        // 김프 계산 공식: ((국내가 / (해외가 * 환율)) - 1) * 100
        double ratio = ((domesticPrice / (foreignPrice * usdKrw)) - 1) * 100;
        
        return KimchPremium.builder()
                .symbol(symbol)
                .domesticExchange(domesticEx)
                .foreignExchange(foreignEx)
                .ratio(ratio)
                .domesticPrice(domesticPrice)
                .foreignPrice(foreignPrice)
                .build();
    }
}
