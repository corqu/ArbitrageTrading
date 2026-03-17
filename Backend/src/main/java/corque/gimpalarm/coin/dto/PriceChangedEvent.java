package corque.gimpalarm.coin.dto;

import lombok.Getter;

@Getter
public class PriceChangedEvent {
    private final String key; // 예: "UB_BTC", "BN_F_BTC"
    private final double price;

    public PriceChangedEvent(String key, double price) {
        this.key = key;
        this.price = price;
    }
}
