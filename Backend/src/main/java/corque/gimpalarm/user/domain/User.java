package corque.gimpalarm.user.domain;

import corque.gimpalarm.board.domain.Board;
import corque.gimpalarm.common.domain.BaseEntity;
import corque.gimpalarm.reply.domain.Reply;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class User extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
    private String password;
    private String nickname;

    @OneToMany(mappedBy = "writer")
    private List<Board> boards = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    private List<Reply> replies = new ArrayList<>();

    public Long getId() {
        return id;
    }
}
