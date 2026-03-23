package corque.gimpalarm.user.domain;

import corque.gimpalarm.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "user_credentials")
public class UserCredential extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String exchange; // e.g., "UPBIT", "BINANCE"

    @Column(nullable = false, length = 1000)
    private String apiKey; // encrypted

    @Column(nullable = false, length = 1000)
    private String apiSecret; // encrypted
}
