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
    private String action;           // START (지정가 진입), STOP (중단), START_AUTO (자동 감시 진입)
    private Double entryKimp;        // 목표 진입 김프 (%)
    private Double exitKimp;         // 목표 탈출 김프 (%)
    private Double stopLossPercent;  // 손절 제한 (%) - 예: 5.0 (5% 손실 시 종료)
    private Double takeProfitPercent; // 익절 목표 (%) - 예: 10.0 (10% 수익 시 종료)
}
