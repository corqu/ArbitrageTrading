package corque.gimpalarm.botstate.domain;

import corque.gimpalarm.coin.domain.BotStatus;
import corque.gimpalarm.common.domain.BaseEntity;
import corque.gimpalarm.user.domain.User;
import corque.gimpalarm.userbot.domain.UserBot;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "bot_trade_states",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_bot_trade_states_user_bot", columnNames = "user_bot_id"),
                @UniqueConstraint(name = "uk_bot_trade_states_bot_key", columnNames = "bot_key")
        })
public class BotTradeState extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_bot_id", nullable = false)
    private UserBot userBot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "bot_key", nullable = false, length = 120)
    private String botKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BotStatus status;

    @Column(length = 100)
    private String domesticOrderId;

    @Column(length = 100)
    private String foreignOrderId;

    private Double totalTargetQty;

    private Double filledQty;

    private Double hedgedQty;

    private Double slPrice;

    private Double tpPrice;

    private LocalDateTime entryTime;

    private LocalDateTime lastCheckedAt;

    @Column(length = 500)
    private String errorReason;
}