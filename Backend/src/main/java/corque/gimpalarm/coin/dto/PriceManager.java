package corque.gimpalarm.coin.dto;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class PriceManager {
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final Map<String, Double> prices = new ConcurrentHashMap<>();
    private final Map<String, Double> fundingRates = new ConcurrentHashMap<>();
    private final Map<String, Double> tradeVolumes = new ConcurrentHashMap<>();
    private final Map<String, Long> nextFundingTimes = new ConcurrentHashMap<>();
    private volatile Double currentUsdKrw;

    public void updatePrice(String key, double price){
        prices.put(key, price);
        eventPublisher.publishEvent(new PriceChangedEvent(key, price));
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
        if (price <= 0) {
            return;
        }

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

    public boolean hasCurrentUsdKrw() {
        return currentUsdKrw != null && currentUsdKrw > 0;
    }

    public Map<String, Double> getAllPrices() {
        return prices;
    }
}
