package corque.gimpalarm.user.domain;

import corque.gimpalarm.board.domain.Board;
import corque.gimpalarm.common.domain.BaseEntity;
import corque.gimpalarm.reply.domain.Reply;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "users")
public class User extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    private String nickname;

    // 업비트 API Keys (암호화되어 저장됨)
    private String upbitAccessKey;
    private String upbitSecretKey;

    // 바이낸스 API Keys (암호화되어 저장됨)
    private String binanceApiKey;
    private String binanceSecretKey;

    @OneToMany(mappedBy = "writer")
    private List<Board> boards = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    private List<Reply> replies = new ArrayList<>();
}
