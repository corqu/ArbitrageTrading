package corque.gimpalarm.tradeorder.repository;

import corque.gimpalarm.tradeorder.domain.TradeOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {
    Optional<TradeOrder> findTopByExchangeAndExchangeOrderIdOrderByIdDesc(String exchange, String exchangeOrderId);
    Optional<TradeOrder> findTopByBotKeyAndOrderRoleOrderByIdDesc(String botKey, String orderRole);
    List<TradeOrder> findAllByBotKeyAndOrderRoleOrderByIdDesc(String botKey, String orderRole);
    List<TradeOrder> findTop200ByUserIdOrderByIdDesc(Long userId);
}
