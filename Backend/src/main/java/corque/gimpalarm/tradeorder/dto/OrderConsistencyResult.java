package corque.gimpalarm.tradeorder.dto;

import corque.gimpalarm.tradeorder.domain.TradeOrder;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderConsistencyResult {

    private final OrderConsistencyStatus status;
    private final TradeOrder domesticOrder;
    private final TradeOrder foreignOrder;
    private final OrderStatusCheckResult domesticCheck;
    private final OrderStatusCheckResult foreignCheck;
    private final String reason;
}