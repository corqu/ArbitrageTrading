package corque.gimpalarm.tradeorder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeOrderHistoryRowDto {
    private String id;
    private String botKey;
    private String symbol;
    private String phase;
    private String exchange;
    private String quantity;
    private String requestedQty;
    private String executedQty;
    private String remainingQty;
    private String requestedPrice;
    private String averagePrice;
    private String status;
}
