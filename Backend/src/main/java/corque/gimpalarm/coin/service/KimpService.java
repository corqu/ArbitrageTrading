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
            Double binanceSpotPrice = priceManager.getPrice("BN_" + symbol); // 현물
            Double binanceFuturesPrice = priceManager.getPrice("BN_F_" + symbol); // 선물
            Double fundingRate = priceManager.getFundingRate(symbol); // 펀딩피

            // 1. 업비트 현물 - 바이낸스 선물 차익거래 (핵심)
            if (upbitPrice != null && binanceFuturesPrice != null) {
                kimpList.add(calculateKimp(symbol, "UPBIT", "BINANCE_FUTURES", 
                                          upbitPrice, binanceFuturesPrice, fundingRate, usdKrw));
            }

            // 2. 업비트 현물 - 바이낸스 현물 (참고용 기존 김프)
            if (upbitPrice != null && binanceSpotPrice != null) {
                kimpList.add(calculateKimp(symbol, "UPBIT", "BINANCE_SPOT", 
                                          upbitPrice, binanceSpotPrice, null, usdKrw));
            }
        }
        return kimpList;
    }

    private KimchPremium calculateKimp(String symbol, String domesticEx, String foreignEx, 
                                      Double domesticPrice, Double foreignPrice, 
                                      Double fundingRate, Double usdKrw) {
        
        // 김프 계산 공식: ((국내가 / (해외가 * 환율)) - 1) * 100
        double ratio = ((domesticPrice / (foreignPrice * usdKrw)) - 1) * 100;

        Double adjustedApr = null;
        Double liquidationPrice = null;

        // 펀딩피가 있고 선물을 사용하는 경우 실질 수익률과 청산가 계산
        if (fundingRate != null && foreignEx.contains("FUTURES")) {
            int leverage = coinConfig.getLeverage();
            
            // 1. 자본 대비 실질 연환산 수익률 (Adjusted APR)
            // 총 투자 자산(C) = 현물 1.0 + (선물 증거금 1.0 / Leverage)
            // 연간 펀딩 수익(I) = 펀딩비 * 3 * 365
            // Adjusted APR = (I / C) * 100
            adjustedApr = ((fundingRate * 3 * 365) / (1.0 + (1.0 / leverage))) * 100;

            // 2. 예상 청산가 (Liquidation Price)
            // 숏 포지션이므로 가격 상승 시 위험. 증거금 소진율 고려 (약 10% 안전 버퍼)
            liquidationPrice = foreignPrice * (1.0 + (0.9 / leverage));
        }
        
        return KimchPremium.builder()
                .symbol(symbol)
                .domesticExchange(domesticEx)
                .foreignExchange(foreignEx)
                .ratio(ratio)
                .fundingRate(fundingRate)
                .adjustedApr(adjustedApr)
                .liquidationPrice(liquidationPrice)
                .build();
    }
}
