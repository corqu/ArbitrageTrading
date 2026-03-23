package corque.gimpalarm.tradeorder.domain;

import corque.gimpalarm.common.domain.BaseEntity;
import corque.gimpalarm.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "trade_orders")
public class TradeOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String botKey;

    @Column(nullable = false, length = 30)
    private String exchange;

    @Column(nullable = false, length = 20)
    private String marketType;

    @Column(nullable = false, length = 20)
    private String orderRole;

    @Column(nullable = false, length = 30)
    private String symbol;

    @Column(nullable = false, length = 20)
    private String side;

    @Column(length = 20)
    private String positionSide;

    @Column(nullable = false, length = 30)
    private String orderType;

    @Column(nullable = false, length = 100)
    private String exchangeOrderId;

    @Column(length = 30)
    private String status;

    private Double requestedQty;

    private Double executedQty;

    private Double remainingQty;

    private Double requestedPrice;

    private Double averagePrice;

    @Lob
    private String responsePayload;
}
