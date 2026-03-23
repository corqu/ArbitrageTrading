package corque.gimpalarm.coin.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "supported_coin")
@Getter
@Setter
@NoArgsConstructor
public class SupportedCoin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String symbol; // 예: BTC, ETH

    private boolean isUpbit;
    private boolean isBithumb;
    private boolean isBinance;
    private boolean isBybit;

    private LocalDateTime lastUpdated;

    public SupportedCoin(String symbol) {
        this.symbol = symbol.toUpperCase();
        this.lastUpdated = LocalDateTime.now();
    }

    public void updateExchanges(boolean up, boolean bt, boolean bn, boolean bb) {
        this.isUpbit = up;
        this.isBithumb = bt;
        this.isBinance = bn;
        this.isBybit = bb;
        this.lastUpdated = LocalDateTime.now();
    }
}
