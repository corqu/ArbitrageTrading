package corque.gimpalarm.botstate.repository;

import corque.gimpalarm.botstate.domain.BotTradeState;
import corque.gimpalarm.coin.domain.BotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BotTradeStateRepository extends JpaRepository<BotTradeState, Long> {
    Optional<BotTradeState> findByBotKey(String botKey);
    Optional<BotTradeState> findByUserBotId(Long userBotId);
    List<BotTradeState> findAllByStatusIn(List<BotStatus> statuses);
}