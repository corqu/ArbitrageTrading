package corque.gimpalarm.coin.service;

import corque.gimpalarm.coin.dto.KimpResponseDto;
import corque.gimpalarm.coin.dto.PriceManager;
import corque.gimpalarm.common.config.CoinConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class KimpService {

    private final PriceManager priceManager;
    private final CoinConfig coinConfig;

    // 수수료 및 슬리피지 설정 (비율로 환산, 0.0005 = 0.05%)
    private static final double UPBIT_FEE = 0.0005;       // 업비트 현물 수수료 (0.05%)
    private static final double BITHUMB_FEE = 0.0004;     // 빗썸 현물 수수료 (기본 0.04% 가정)
    private static final double BINANCE_FUTURES_FEE = 0.0002; // 바이낸스 선물 수수료
    private static final double SLIPPAGE = 0.001;         // 예상 슬리피지 (0.1%)

    public Map<String, List<KimpResponseDto>> calculateAllPairs() {
        Double usdKrw = priceManager.getCurrentUsdKrw();
        Map<String, List<KimpResponseDto>> result = new HashMap<>();
        
        result.put("ub-bn", new ArrayList<>());
        result.put("ub-bb", new ArrayList<>());
        result.put("bt-bn", new ArrayList<>());
        result.put("bt-bb", new ArrayList<>());

        if (usdKrw == null || usdKrw == 0) return result;

        priceManager.getAllPrices().keySet().stream()
            .filter(key -> key.startsWith("UB_") || key.startsWith("BT_"))
            .map(key -> key.substring(3))
            .distinct()
            .forEach(symbol -> {
                Double ubPrice = priceManager.getPrice("UB_" + symbol);
                Double btPrice = priceManager.getPrice("BT_" + symbol);
                Double bnPrice = priceManager.getPrice("BN_F_" + symbol);
                Double bbPrice = priceManager.getPrice("BY_" + symbol); // Bybit 가격

                Double fundingRate = priceManager.getFundingRate(symbol);
                Double ubVolume = priceManager.getTradeVolume("UB_" + symbol);
                Double btVolume = priceManager.getTradeVolume("BT_" + symbol);

                // 1. Upbit - Binance
                if (ubPrice != null && bnPrice != null) {
                    result.get("ub-bn").add(calculateKimp(symbol, "UPBIT", "BINANCE_FUTURES", 
                        ubPrice, bnPrice, fundingRate, usdKrw, ubVolume, UPBIT_FEE));
                }
                // 2. Upbit - Bybit
                if (ubPrice != null && bbPrice != null) {
                    result.get("ub-bb").add(calculateKimp(symbol, "UPBIT", "BYBIT_FUTURES", 
                        ubPrice, bbPrice, fundingRate, usdKrw, ubVolume, UPBIT_FEE));
                }
                // 3. Bithumb - Binance
                if (btPrice != null && bnPrice != null) {
                    result.get("bt-bn").add(calculateKimp(symbol, "BITHUMB", "BINANCE_FUTURES", 
                        btPrice, bnPrice, fundingRate, usdKrw, btVolume, BITHUMB_FEE));
                }
                // 4. Bithumb - Bybit
                if (btPrice != null && bbPrice != null) {
                    result.get("bt-bb").add(calculateKimp(symbol, "BITHUMB", "BYBIT_FUTURES", 
                        btPrice, bbPrice, fundingRate, usdKrw, btVolume, BITHUMB_FEE));
                }
            });

        return result;
    }

    // 기존 calculateAllKimp는 하위 호환성을 위해 유지하거나 호출부 수정 필요
    public List<KimpResponseDto> calculateAllKimp() {
        Map<String, List<KimpResponseDto>> pairs = calculateAllPairs();
        List<KimpResponseDto> all = new ArrayList<>();
        all.addAll(pairs.get("ub-bn"));
        all.addAll(pairs.get("bt-bn"));
        return all;
    }

    private KimpResponseDto calculateKimp(String symbol, String domesticEx, String foreignEx, 
                                      Double domesticPrice, Double foreignPrice, 
                                      Double fundingRate, Double usdKrw, Double tradeVolume, double domesticFee) {
        
        double ratio = ((domesticPrice / (foreignPrice * usdKrw)) - 1) * 100;
        Double adjustedApr = null;
        Double liquidationPrice = null;

        if (fundingRate != null && foreignEx.contains("FUTURES")) {
            int leverage = coinConfig.getLeverage();
            double totalCapital = 1.0 + (1.0 / leverage);
            double annualFundingIncome = fundingRate * 3 * 365;
            double entryExitCost = (domesticFee * 2) + (BINANCE_FUTURES_FEE * 2) + (SLIPPAGE * 2);
            adjustedApr = ((annualFundingIncome - entryExitCost) / totalCapital) * 100;
            liquidationPrice = foreignPrice * (1.0 + (0.9 / leverage));
        }
        
        return KimpResponseDto.builder()
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
