package corque.gimpalarm.coin.dto;

import lombok.Data;

@Data
public class TradingRequest {
    private String symbol;      // 코인 심볼 (예: BTC)
    private double amountKrw;   // 투자할 총 금액 (원화 기준)
    private int leverage;       // 사용할 레버리지 배수
    private String action;      // START (진입), STOP (청산/종료)
}
