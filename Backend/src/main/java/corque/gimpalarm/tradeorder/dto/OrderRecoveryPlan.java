package corque.gimpalarm.tradeorder.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderRecoveryPlan {

    private final boolean markBotAsError;
    private final boolean cancelDomesticOrder;
    private final boolean cancelForeignOrder;
    private final boolean closeDomesticPosition;
    private final boolean closeForeignPosition;
    private final boolean manualReviewRequired;
    private final String reason;
}