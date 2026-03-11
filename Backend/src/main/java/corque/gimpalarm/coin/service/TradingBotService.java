package corque.gimpalarm.coin.service;

import corque.gimpalarm.coin.dto.TradingRequest;
import corque.gimpalarm.common.config.CoinConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class TradingBotService {

    private final CoinConfig coinConfig;
    private final Map<String, Boolean> botStatusMap = new ConcurrentHashMap<>(); // 코인별 봇 작동 상태

    /**
     * 사용자의 매매 명령을 처리합니다. (START / STOP)
     */
    public String executeTrade(TradingRequest request) {
        String symbol = request.getSymbol().toUpperCase();
        
        if ("START".equalsIgnoreCase(request.getAction())) {
            return startArbitrage(request);
        } else if ("STOP".equalsIgnoreCase(request.getAction())) {
            return stopArbitrage(symbol);
        }
        
        return "알 수 없는 명령입니다.";
    }

    private String startArbitrage(TradingRequest request) {
        String symbol = request.getSymbol().toUpperCase();
        
        if (botStatusMap.getOrDefault(symbol, false)) {
            return symbol + " 봇이 이미 실행 중입니다.";
        }

        log.info(">>> 아비트리지 진입 시작: {} (금액: {} KRW, 레버리지: {}배)", 
                 symbol, request.getAmountKrw(), request.getLeverage());

        try {
            // 1. 업비트 현물 시장가 매수 (예상 로직)
            // double boughtAmount = upbitClient.buyMarket(symbol, request.getAmountKrw());
            
            // 2. 바이낸스 선물 동일 수량 숏 (예상 로직)
            // binanceClient.shortFutures(symbol, boughtAmount, request.getLeverage());

            botStatusMap.put(symbol, true);
            return symbol + " 아비트리지 진입 성공!";
        } catch (Exception e) {
            log.error("매매 실패: {}", e.getMessage());
            return "매매 중 오류 발생: " + e.getMessage();
        }
    }

    private String stopArbitrage(String symbol) {
        if (!botStatusMap.getOrDefault(symbol, false)) {
            return symbol + " 봇이 실행 중이 아닙니다.";
        }

        log.info(">>> 아비트리지 포지션 청산 시작: {}", symbol);

        try {
            // 1. 바이낸스 선물 숏 청산
            // binanceClient.closeFutures(symbol);
            
            // 2. 업비트 현물 전량 매도
            // upbitClient.sellAll(symbol);

            botStatusMap.put(symbol, false);
            return symbol + " 아비트리지 청산 완료!";
        } catch (Exception e) {
            log.error("청산 실패: {}", e.getMessage());
            return "청산 중 오류 발생: " + e.getMessage();
        }
    }

    public Map<String, Boolean> getBotStatus() {
        return botStatusMap;
    }
}
