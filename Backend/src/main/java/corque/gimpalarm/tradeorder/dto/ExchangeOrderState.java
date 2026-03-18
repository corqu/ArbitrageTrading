package corque.gimpalarm.tradeorder.dto;

public enum ExchangeOrderState {
    OPEN,
    PARTIALLY_FILLED,
    FILLED,
    CANCELED,
    FETCH_FAILED,
    UNKNOWN
}