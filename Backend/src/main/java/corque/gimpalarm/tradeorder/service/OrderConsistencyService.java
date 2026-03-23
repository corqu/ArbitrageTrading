package corque.gimpalarm.tradeorder.service;

import corque.gimpalarm.tradeorder.domain.TradeOrder;
import corque.gimpalarm.tradeorder.dto.ExchangeOrderState;
import corque.gimpalarm.tradeorder.dto.OrderConsistencyResult;
import corque.gimpalarm.tradeorder.dto.OrderConsistencyStatus;
import corque.gimpalarm.tradeorder.dto.OrderStatusCheckResult;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class OrderConsistencyService {

    public OrderConsistencyResult evaluate(TradeOrder domesticOrder, OrderStatusCheckResult domesticCheck,
                                           TradeOrder foreignOrder, OrderStatusCheckResult foreignCheck) {
        if (domesticCheck == null || foreignCheck == null) {
            return buildResult(OrderConsistencyStatus.UNKNOWN, domesticOrder, domesticCheck, foreignOrder, foreignCheck,
                    "One or more order checks are missing");
        }

        if (!domesticCheck.isFetchSucceeded() || !foreignCheck.isFetchSucceeded()) {
            return buildResult(OrderConsistencyStatus.UNKNOWN, domesticOrder, domesticCheck, foreignOrder, foreignCheck,
                    "Order fetch failed or returned incomplete data");
        }

        ExchangeOrderState expectedDomestic = mapStoredStatus(domesticOrder != null ? domesticOrder.getStatus() : null);
        ExchangeOrderState expectedForeign = mapStoredStatus(foreignOrder != null ? foreignOrder.getStatus() : null);

        boolean domesticMatches = statesMatch(expectedDomestic, domesticCheck.getResolvedState());
        boolean foreignMatches = statesMatch(expectedForeign, foreignCheck.getResolvedState());

        if (domesticMatches && foreignMatches) {
            return buildResult(OrderConsistencyStatus.MATCHED, domesticOrder, domesticCheck, foreignOrder, foreignCheck,
                    "Stored order state matches exchange state");
        }

        if (isActionable(domesticCheck.getResolvedState()) ^ isActionable(foreignCheck.getResolvedState())) {
            return buildResult(OrderConsistencyStatus.RECOVERABLE_MISMATCH, domesticOrder, domesticCheck, foreignOrder, foreignCheck,
                    "Only one leg still has an actionable order or position state");
        }

        return buildResult(OrderConsistencyStatus.MISMATCHED, domesticOrder, domesticCheck, foreignOrder, foreignCheck,
                "Stored order state does not match exchange state");
    }

    private OrderConsistencyResult buildResult(OrderConsistencyStatus status,
                                               TradeOrder domesticOrder,
                                               OrderStatusCheckResult domesticCheck,
                                               TradeOrder foreignOrder,
                                               OrderStatusCheckResult foreignCheck,
                                               String reason) {
        return OrderConsistencyResult.builder()
                .status(status)
                .domesticOrder(domesticOrder)
                .foreignOrder(foreignOrder)
                .domesticCheck(domesticCheck)
                .foreignCheck(foreignCheck)
                .reason(reason)
                .build();
    }

    private ExchangeOrderState mapStoredStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "WAIT", "NEW" -> ExchangeOrderState.OPEN;
            case "PARTIALLY_FILLED", "PARTIAL" -> ExchangeOrderState.PARTIALLY_FILLED;
            case "FILLED", "DONE" -> ExchangeOrderState.FILLED;
            case "CANCELED", "CANCELLED", "CANCEL" -> ExchangeOrderState.CANCELED;
            default -> ExchangeOrderState.UNKNOWN;
        };
    }

    private boolean statesMatch(ExchangeOrderState expected, ExchangeOrderState actual) {
        if (expected == ExchangeOrderState.UNKNOWN || actual == ExchangeOrderState.UNKNOWN) {
            return false;
        }
        return expected == actual;
    }

    private boolean isActionable(ExchangeOrderState state) {
        return state == ExchangeOrderState.OPEN || state == ExchangeOrderState.PARTIALLY_FILLED || state == ExchangeOrderState.FILLED;
    }
}