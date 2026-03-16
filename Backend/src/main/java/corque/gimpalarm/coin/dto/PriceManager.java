package corque.gimpalarm.coin.dto;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PriceManager {
    private final Map<String, Double> prices = new ConcurrentHashMap<>();
    private final Map<String, Double> fundingRates = new ConcurrentHashMap<>();
    private final Map<String, Double> tradeVolumes = new ConcurrentHashMap<>();
    private final Map<String, Long> nextFundingTimes = new ConcurrentHashMap<>();
    private Double currentUsdKrw;

    public void updatePrice(String key, double price){
        prices.put(key, price);
    }

    public void updateTradeVolume(String key, double volume) {
        tradeVolumes.put(key, volume);
    }

    public Double getTradeVolume(String key) {
        return tradeVolumes.get(key);
    }

    public void updateFundingRate(String coin, double rate, long nextTime) {
        fundingRates.put(coin, rate);
        nextFundingTimes.put(coin, nextTime);
    }

    public void updateUsdKrw(double price){
        currentUsdKrw = price;
    }

    public Double getPrice(String key){
        return prices.get(key);
    }

    public Double getFundingRate(String coin) {
        return fundingRates.get(coin);
    }

    public Long getNextFundingTime(String coin) {
        return nextFundingTimes.get(coin);
    }

    public Double getCurrentUsdKrw() {
        return currentUsdKrw;
    }

    public Map<String, Double> getAllPrices() {
        return prices;
    }
}
