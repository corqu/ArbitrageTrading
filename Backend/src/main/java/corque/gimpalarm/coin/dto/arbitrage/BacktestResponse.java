package corque.gimpalarm.coin.dto.arbitrage;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BacktestResponse {
    private String symbol;
    private double entryThreshold;
    private double exitThreshold;
    
    private int totalTrades;
    private double avgAnnualTrades;
    private double avgHoldingDays;
    
    private double totalReturn;      // 총 수익률 (김프수익 + 펀딩수익)
    private double kimpReturn;       // 김프 차이로 얻은 수익률
    private double fundingReturn;    // 보유 기간 중 받은 펀딩비 총합
    
    private int fundingCount;        // 펀딩비를 받은 횟수
    private double winRate;
    
    private String message;
}
