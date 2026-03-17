package corque.gimpalarm.userbot.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserBotResponseDto {
    private Long id;
    private String symbol;
    private String domesticExchange;
    private String foreignExchange;
    private double amountKrw;
    private Double limitPrice;
    private int leverage;
    private String action;
    private Double entryKimp;
    private Double exitKimp;
    private Double stopLossPercent;
    private Double takeProfitPercent;
    private boolean isActive;
}
