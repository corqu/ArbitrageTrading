package corque.gimpalarm.userbot.repository;

import corque.gimpalarm.userbot.domain.UserBot;
import corque.gimpalarm.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserBotRepository extends JpaRepository<UserBot, Long> {
    List<UserBot> findAllByUser(User user);
    Optional<UserBot> findByIdAndUser(Long id, User user);
    List<UserBot> findAllByIsActiveTrue();
}
