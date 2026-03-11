package corque.gimpalarm.coin.dto;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PriceManager {
    private final Map<String, Double> prices = new ConcurrentHashMap<>();
    private Double currentUsdKrw;

    public void updatePrice(String key, double price){
        prices.put(key, price);
    }

    public void updateUsdKrw(double price){
        currentUsdKrw = price;
    }

    public Double getPrice(String key){
        return prices.get(key);
    }

    public Double getCurrentUsdKrw() {
        return currentUsdKrw;
    }

    public Map<String, Double> getAllPrices() {
        return prices;
    }
}
