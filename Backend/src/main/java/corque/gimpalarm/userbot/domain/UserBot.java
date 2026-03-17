package corque.gimpalarm.userbot.domain;

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
@Table(name = "user_bots")
public class UserBot extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String domesticExchange;

    @Column(nullable = false)
    private String foreignExchange;

    @Column(nullable = false)
    private double amountKrw;

    private Double limitPrice;

    @Column(nullable = false)
    private int leverage;

    @Column(nullable = false)
    private String action;

    private Double entryKimp;
    private Double exitKimp;
    private Double stopLossPercent;
    private Double takeProfitPercent;

    @Builder.Default
    @Column(nullable = false)
    private boolean isActive = true;
}
