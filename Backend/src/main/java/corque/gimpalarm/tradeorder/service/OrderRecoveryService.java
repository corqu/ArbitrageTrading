package corque.gimpalarm.tradeorder.service;

import corque.gimpalarm.tradeorder.dto.ExchangeOrderState;
import corque.gimpalarm.tradeorder.dto.OrderConsistencyResult;
import corque.gimpalarm.tradeorder.dto.OrderConsistencyStatus;
import corque.gimpalarm.tradeorder.dto.OrderRecoveryPlan;
import corque.gimpalarm.tradeorder.dto.OrderStatusCheckResult;
import org.springframework.stereotype.Service;

@Service
public class OrderRecoveryService {

    public OrderRecoveryPlan plan(OrderConsistencyResult consistencyResult) {
        if (consistencyResult == null) {
            return OrderRecoveryPlan.builder()
                    .manualReviewRequired(true)
                    .reason("Consistency result is missing")
                    .build();
        }

        if (consistencyResult.getStatus() == OrderConsistencyStatus.MATCHED) {
            return OrderRecoveryPlan.builder()
                    .reason("No recovery action required")
                    .build();
        }

        if (consistencyResult.getStatus() == OrderConsistencyStatus.UNKNOWN) {
            return OrderRecoveryPlan.builder()
                    .manualReviewRequired(true)
                    .reason(consistencyResult.getReason())
                    .build();
        }

        RecoveryAction domesticAction = determineAction(consistencyResult.getDomesticCheck());
        RecoveryAction foreignAction = determineAction(consistencyResult.getForeignCheck());

        return OrderRecoveryPlan.builder()
                .markBotAsError(true)
                .cancelDomesticOrder(domesticAction.cancelOrder)
                .cancelForeignOrder(foreignAction.cancelOrder)
                .closeDomesticPosition(domesticAction.closePosition)
                .closeForeignPosition(foreignAction.closePosition)
                .manualReviewRequired(consistencyResult.getStatus() == OrderConsistencyStatus.MISMATCHED)
                .reason(consistencyResult.getReason())
                .build();
    }

    private RecoveryAction determineAction(OrderStatusCheckResult checkResult) {
        if (checkResult == null || !checkResult.isFetchSucceeded()) {
            return RecoveryAction.NONE;
        }

        return switch (checkResult.getResolvedState()) {
            case OPEN -> RecoveryAction.CANCEL_ONLY;
            case PARTIALLY_FILLED -> RecoveryAction.CANCEL_AND_CLOSE;
            case FILLED -> RecoveryAction.CLOSE_ONLY;
            default -> RecoveryAction.NONE;
        };
    }

    private enum RecoveryAction {
        NONE(false, false),
        CANCEL_ONLY(true, false),
        CLOSE_ONLY(false, true),
        CANCEL_AND_CLOSE(true, true);

        private final boolean cancelOrder;
        private final boolean closePosition;

        RecoveryAction(boolean cancelOrder, boolean closePosition) {
            this.cancelOrder = cancelOrder;
            this.closePosition = closePosition;
        }
    }
}