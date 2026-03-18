package corque.gimpalarm.userbot.repository;

import corque.gimpalarm.user.domain.User;
import corque.gimpalarm.userbot.domain.UserBot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserBotRepository extends JpaRepository<UserBot, Long> {
    List<UserBot> findAllByUser(User user);
    Optional<UserBot> findByIdAndUser(Long id, User user);
    List<UserBot> findAllByIsActiveTrue();
    Optional<UserBot> findByUserIdAndSymbolIgnoreCaseAndDomesticExchangeIgnoreCaseAndForeignExchangeIgnoreCase(
            Long userId, String symbol, String domesticExchange, String foreignExchange);
}
