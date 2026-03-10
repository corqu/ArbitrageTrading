package corque.gimpalarm.board.domain;

import corque.gimpalarm.common.domain.BaseEntity;
import corque.gimpalarm.image.domain.Image;
import corque.gimpalarm.reply.domain.Reply;
import corque.gimpalarm.user.domain.User;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Board extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "user_id")
    private User writer;

    @OneToMany(mappedBy = "board")
    private List<Image> images = new ArrayList<>();

    @OneToMany(mappedBy = "board")
    private List<Reply> replies = new ArrayList<>();
}
