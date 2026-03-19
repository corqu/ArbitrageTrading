package corque.gimpalarm.userbot.domain;

import corque.gimpalarm.botstate.domain.BotTradeState;
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

    @OneToOne(mappedBy = "userBot", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = true)
    private BotTradeState botTradeState;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String domesticExchange;

    @Column(nullable = false)
    private String foreignExchange;

    @Column(nullable = false)
    private double amountKrw;

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

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserBotStatus status = UserBotStatus.WAITING;

    public void setBotTradeState(BotTradeState botTradeState) {
        this.botTradeState = botTradeState;
        if (botTradeState != null && botTradeState.getUserBot() != this) {
            botTradeState.setUserBot(this);
        }
    }
}
