package corque.gimpalarm.coin.service;

import corque.gimpalarm.coin.dto.KimpResponseDto;
import corque.gimpalarm.coin.dto.PriceManager;
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

    private static final double UPBIT_FEE = 0.0005;
    private static final double BITHUMB_FEE = 0.0004;
    private static final double BINANCE_FUTURES_FEE = 0.0002;
    private static final double SLIPPAGE = 0.001;

    public Map<String, List<KimpResponseDto>> calculateAllPairs() {
        Double currentUsdKrw = priceManager.getCurrentUsdKrw();
        double usdKrw = (currentUsdKrw == null || currentUsdKrw <= 0) ? 1450.0 : currentUsdKrw;

        Map<String, List<KimpResponseDto>> result = new HashMap<>();
        result.put("ub-bn", new ArrayList<>());
        result.put("ub-bb", new ArrayList<>());
        result.put("bt-bn", new ArrayList<>());
        result.put("bt-bb", new ArrayList<>());

        priceManager.getAllPrices().keySet().stream()
                .filter(key -> key.startsWith("UB_") || key.startsWith("BT_"))
                .map(key -> key.substring(3))
                .distinct()
                .forEach(symbol -> {
                    Double ubPrice = priceManager.getPrice("UB_" + symbol);
                    Double ubBestAsk = priceManager.getBestAsk("UB_" + symbol);
                    Double ubBestBid = priceManager.getBestBid("UB_" + symbol);
                    Double btPrice = priceManager.getPrice("BT_" + symbol);
                    Double btBestAsk = priceManager.getBestAsk("BT_" + symbol);
                    Double btBestBid = priceManager.getBestBid("BT_" + symbol);
                    Double bnPrice = priceManager.getPrice("BN_F_" + symbol);
                    Double bbPrice = priceManager.getPrice("BY_" + symbol);

                    Double fundingRate = priceManager.getFundingRate(symbol);
                    Double ubVolume = priceManager.getTradeVolume("UB_" + symbol);
                    Double btVolume = priceManager.getTradeVolume("BT_" + symbol);

                    if ((ubBestAsk != null || ubPrice != null) && bnPrice != null) {
                        KimpResponseDto kimp = calculateKimp(symbol, "UPBIT", "BINANCE_FUTURES",
                                ubPrice,
                                ubBestAsk != null ? ubBestAsk : ubPrice,
                                ubBestBid != null ? ubBestBid : ubPrice,
                                bnPrice,
                                fundingRate,
                                usdKrw,
                                ubVolume);
                        if (kimp != null) result.get("ub-bn").add(kimp);
                    }
                    if ((ubBestAsk != null || ubPrice != null) && bbPrice != null) {
                        KimpResponseDto kimp = calculateKimp(symbol, "UPBIT", "BYBIT_FUTURES",
                                ubPrice,
                                ubBestAsk != null ? ubBestAsk : ubPrice,
                                ubBestBid != null ? ubBestBid : ubPrice,
                                bbPrice,
                                fundingRate,
                                usdKrw,
                                ubVolume);
                        if (kimp != null) result.get("ub-bb").add(kimp);
                    }
                    if ((btBestAsk != null || btPrice != null) && bnPrice != null) {
                        KimpResponseDto kimp = calculateKimp(symbol, "BITHUMB", "BINANCE_FUTURES",
                                btPrice,
                                btBestAsk != null ? btBestAsk : btPrice,
                                btBestBid != null ? btBestBid : btPrice,
                                bnPrice,
                                fundingRate,
                                usdKrw,
                                btVolume);
                        if (kimp != null) result.get("bt-bn").add(kimp);
                    }
                    if ((btBestAsk != null || btPrice != null) && bbPrice != null) {
                        KimpResponseDto kimp = calculateKimp(symbol, "BITHUMB", "BYBIT_FUTURES",
                                btPrice,
                                btBestAsk != null ? btBestAsk : btPrice,
                                btBestBid != null ? btBestBid : btPrice,
                                bbPrice,
                                fundingRate,
                                usdKrw,
                                btVolume);
                        if (kimp != null) result.get("bt-bb").add(kimp);
                    }
                });

        return result;
    }

    public List<KimpResponseDto> calculateAllKimp() {
        Map<String, List<KimpResponseDto>> pairs = calculateAllPairs();
        List<KimpResponseDto> all = new ArrayList<>();
        pairs.values().forEach(all::addAll);
        return all;
    }

    public Map<String, KimpResponseDto> calculatePairsForSymbol(String symbol) {
        Double currentUsdKrw = priceManager.getCurrentUsdKrw();
        double usdKrw = (currentUsdKrw == null || currentUsdKrw <= 0) ? 1450.0 : currentUsdKrw;

        String upperSymbol = symbol.toUpperCase();
        Double ubPrice = priceManager.getPrice("UB_" + upperSymbol);
        Double ubBestAsk = priceManager.getBestAsk("UB_" + upperSymbol);
        Double ubBestBid = priceManager.getBestBid("UB_" + upperSymbol);
        Double btPrice = priceManager.getPrice("BT_" + upperSymbol);
        Double btBestAsk = priceManager.getBestAsk("BT_" + upperSymbol);
        Double btBestBid = priceManager.getBestBid("BT_" + upperSymbol);
        Double bnPrice = priceManager.getPrice("BN_F_" + upperSymbol);
        Double bbPrice = priceManager.getPrice("BY_" + upperSymbol);
        Double fundingRate = priceManager.getFundingRate(upperSymbol);
        Double ubVolume = priceManager.getTradeVolume("UB_" + upperSymbol);
        Double btVolume = priceManager.getTradeVolume("BT_" + upperSymbol);

        Map<String, KimpResponseDto> result = new HashMap<>();
        result.put("ub-bn", (ubBestAsk != null || ubPrice != null) && bnPrice != null
                ? calculateKimp(upperSymbol, "UPBIT", "BINANCE_FUTURES", ubPrice,
                ubBestAsk != null ? ubBestAsk : ubPrice,
                ubBestBid != null ? ubBestBid : ubPrice,
                bnPrice, fundingRate, usdKrw, ubVolume)
                : null);
        result.put("ub-bb", (ubBestAsk != null || ubPrice != null) && bbPrice != null
                ? calculateKimp(upperSymbol, "UPBIT", "BYBIT_FUTURES", ubPrice,
                ubBestAsk != null ? ubBestAsk : ubPrice,
                ubBestBid != null ? ubBestBid : ubPrice,
                bbPrice, fundingRate, usdKrw, ubVolume)
                : null);
        result.put("bt-bn", (btBestAsk != null || btPrice != null) && bnPrice != null
                ? calculateKimp(upperSymbol, "BITHUMB", "BINANCE_FUTURES", btPrice,
                btBestAsk != null ? btBestAsk : btPrice,
                btBestBid != null ? btBestBid : btPrice,
                bnPrice, fundingRate, usdKrw, btVolume)
                : null);
        result.put("bt-bb", (btBestAsk != null || btPrice != null) && bbPrice != null
                ? calculateKimp(upperSymbol, "BITHUMB", "BYBIT_FUTURES", btPrice,
                btBestAsk != null ? btBestAsk : btPrice,
                btBestBid != null ? btBestBid : btPrice,
                bbPrice, fundingRate, usdKrw, btVolume)
                : null);
        return result;
    }

    private KimpResponseDto calculateKimp(String symbol, String domesticEx, String foreignEx,
                                          Double standardDomesticPrice, Double entryDomesticPrice, Double exitDomesticPrice, Double foreignPrice,
                                          Double fundingRate, Double usdKrw, Double tradeVolume) {

        if ((standardDomesticPrice == null || standardDomesticPrice <= 0)
                && (entryDomesticPrice == null || entryDomesticPrice <= 0)) {
            return null;
        }
        if (foreignPrice == null || foreignPrice <= 0) {
            return null;
        }

        Double standardRatio = standardDomesticPrice != null && standardDomesticPrice > 0
                ? calculateRatio(standardDomesticPrice, foreignPrice, usdKrw)
                : null;
        Double entryRatio = entryDomesticPrice != null && entryDomesticPrice > 0
                ? calculateRatio(entryDomesticPrice, foreignPrice, usdKrw)
                : standardRatio;
        Double exitRatio = exitDomesticPrice != null && exitDomesticPrice > 0
                ? calculateRatio(exitDomesticPrice, foreignPrice, usdKrw)
                : standardRatio;
        return KimpResponseDto.builder()
                .symbol(symbol)
                .domesticExchange(domesticEx)
                .foreignExchange(foreignEx)
                .standardRatio(standardRatio)
                .entryRatio(entryRatio)
                .exitRatio(exitRatio)
                .standardDomesticPrice(standardDomesticPrice)
                .entryDomesticPrice(entryDomesticPrice)
                .exitDomesticPrice(exitDomesticPrice)
                .foreignPrice(foreignPrice)
                .usdKrw(usdKrw)
                .fundingRate(fundingRate)
                .tradeVolume(tradeVolume)
                .build();
    }

    private double calculateRatio(double domesticPrice, double foreignPrice, double usdKrw) {
        return ((domesticPrice / (foreignPrice * usdKrw)) - 1) * 100;
    }
}
