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
    private final TelegramService telegramService;

    // 수수료 및 슬리피지 설정 (비율로 환산, 0.0005 = 0.05%)
    private static final double UPBIT_FEE = 0.0005;       // 업비트 현물 수수료 (0.05%)
    private static final double BINANCE_FUTURES_FEE = 0.0002; // 바이낸스 선물 수수료
    private static final double SLIPPAGE = 0.001;         // 예상 슬리피지 (0.1%)

    @Scheduled(fixedRate = 60000)
    public void calculateAndSaveKimp() {
        List<KimchPremium> kimpList = calculateAllKimp();
        if (!kimpList.isEmpty()) {
            coinPriceService.saveKimchPremiums(kimpList);
            log.info("1분 단위 김프 배치 저장 완료 ({} 건)", kimpList.size());
        }
    }

    public List<KimchPremium> getCurrentKimpList() {
        List<KimchPremium> kimpList = calculateAllKimp();
        if (!kimpList.isEmpty()) {
            kimpList.sort((a, b) -> {
                Double aprA = a.getAdjustedApr() != null ? a.getAdjustedApr() : -100.0;
                Double aprB = b.getAdjustedApr() != null ? b.getAdjustedApr() : -100.0;
                return aprB.compareTo(aprA);
            });
        }
        return kimpList;
    }

    @Scheduled(fixedRate = 500)
    public void sendRealTimeKimp() {
        List<KimchPremium> kimpList = getCurrentKimpList();
        if (!kimpList.isEmpty()) {
            for (KimchPremium kimp : kimpList) {
                if ("BINANCE_FUTURES".equals(kimp.getForeignExchange()) && 
                    kimp.getAdjustedApr() != null && 
                    kimp.getAdjustedApr() >= coinConfig.getNotificationThreshold()) {
                    
                    if (telegramService.shouldSendAlert(kimp.getSymbol())) {
                        String msg = String.format("[차익거래 알림] %s\n수익률(APR): %.2f%%\n김프: %.2f%%\n펀딩비: %.4f%%", 
                                                    kimp.getSymbol(), kimp.getAdjustedApr(), kimp.getRatio(), kimp.getFundingRate());
                        telegramService.sendMessage(msg);
                    }
                }
            }
            messagingTemplate.convertAndSend("/topic/kimp", kimpList);
        }
    }

    private List<KimchPremium> calculateAllKimp() {
        Double usdKrw = priceManager.getCurrentUsdKrw();
        List<KimchPremium> kimpList = new ArrayList<>();

        if (usdKrw == null || usdKrw == 0) {
            return kimpList;
        }

        priceManager.getAllPrices().keySet().stream()
            .filter(key -> key.startsWith("UB_"))
            .map(key -> key.substring(3))
            .forEach(symbol -> {
                Double upbitPrice = priceManager.getPrice("UB_" + symbol);
                Double binanceFuturesPrice = priceManager.getPrice("BN_F_" + symbol);
                Double fundingRate = priceManager.getFundingRate(symbol);
                Double tradeVolume = priceManager.getTradeVolume(symbol); // 거래대금

                if (upbitPrice != null && binanceFuturesPrice != null) {
                    kimpList.add(calculateKimp(symbol, "UPBIT", "BINANCE_FUTURES", 
                                              upbitPrice, binanceFuturesPrice, fundingRate, usdKrw, tradeVolume));
                }
            });

        return kimpList;
    }

    private KimchPremium calculateKimp(String symbol, String domesticEx, String foreignEx, 
                                      Double domesticPrice, Double foreignPrice, 
                                      Double fundingRate, Double usdKrw, Double tradeVolume) {
        
        double ratio = ((domesticPrice / (foreignPrice * usdKrw)) - 1) * 100;
        Double adjustedApr = null;
        Double liquidationPrice = null;

        if (fundingRate != null && foreignEx.contains("FUTURES")) {
            int leverage = coinConfig.getLeverage();
            double totalCapital = 1.0 + (1.0 / leverage);
            double annualFundingIncome = fundingRate * 3 * 365;
            double entryExitCost = (UPBIT_FEE * 2) + (BINANCE_FUTURES_FEE * 2) + (SLIPPAGE * 2);
            adjustedApr = ((annualFundingIncome - entryExitCost) / totalCapital) * 100;
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
                .tradeVolume(tradeVolume)
                .build();
    }
}
