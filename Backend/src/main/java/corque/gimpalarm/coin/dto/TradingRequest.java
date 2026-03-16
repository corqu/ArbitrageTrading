package corque.gimpalarm.coin.dto;

import lombok.Data;

@Data
public class TradingRequest {
    private String symbol;           // 코인 심볼 (예: BTC)
    private String domesticExchange; // UPBIT, BITHUMB
    private String foreignExchange;  // BINANCE, BYBIT
    private double amountKrw;        // 투자할 총 금액 (원화 기준)
    private Double limitPrice;       // 지정가 매수 가격 (null이면 현재가/시장가)
    private int leverage;            // 사용할 레버리지 배수
    private String action;           // START (진입), STOP (청산/종료)
}
