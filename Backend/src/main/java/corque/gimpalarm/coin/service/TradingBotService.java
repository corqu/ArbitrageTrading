package corque.gimpalarm.coin.service;

import corque.gimpalarm.coin.dto.TradingRequest;
import corque.gimpalarm.common.config.CoinConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class TradingBotService {

    private final CoinConfig coinConfig;

    // 실행 중인 봇의 정보를 담는 객체
    @Data
    @AllArgsConstructor
    private static class ActiveTrade {
        private TradingRequest request;
        private String domesticOrderId;   // 국내 거래소 주문 ID
        private double totalTargetQty;    // 목표 총 수량
        private double filledQty;         // 현재까지 체결된 수량
        private double hedgedQty;         // 현재까지 해외에서 헷지(숏)한 수량
    }

    private final Map<String, ActiveTrade> activeBots = new ConcurrentHashMap<>();

    /**
     * 사용자의 매매 명령을 처리합니다.
     */
    public String executeTrade(TradingRequest request) {
        String botKey = String.format("%s:%s:%s",
                request.getSymbol().toUpperCase(),
                request.getDomesticExchange().toUpperCase(),
                request.getForeignExchange().toUpperCase());

        if ("START".equalsIgnoreCase(request.getAction())) {
            return startLimitArbitrage(request, botKey);
        } else if ("STOP".equalsIgnoreCase(request.getAction())) {
            return stopArbitrage(botKey);
        }
        return "알 수 없는 명령입니다.";
    }

    private String startLimitArbitrage(TradingRequest request, String botKey) {
        if (activeBots.containsKey(botKey)) {
            return botKey + " 봇이 이미 실행 중입니다.";
        }

        log.info(">>> 지정가 아비트리지 진입: {} @ {} KRW", botKey, request.getLimitPrice());

        try {
            // 1. 국내 거래소 지정가 주문 (업비트/빗썸)
            String orderId = "ORD-12345"; // 가상 ID
            double targetQty = request.getAmountKrw() / request.getLimitPrice();

            if ("UPBIT".equals(request.getDomesticExchange())) {
                log.info("업비트 지정가 매수 주문 전송: {} qty", targetQty);
            } else if ("BITHUMB".equals(request.getDomesticExchange())) {
                log.info("빗썸 지정가 매수 주문 전송: {} qty", targetQty);
            }

            // 2. 관리 목록에 추가 (감시 시작)
            activeBots.put(botKey, new ActiveTrade(request, orderId, targetQty, 0.0, 0.0));

            return botKey + " 지정가 주문 완료. 체결 감시를 시작합니다.";
        } catch (Exception e) {
            log.error("진입 실패: {}", e.getMessage());
            return "오류 발생: " + e.getMessage();
        }
    }

    /**
     * 1초마다 실행되며 모든 활성 봇의 체결 상태를 확인하고 헷지 주문을 실행합니다.
     */
    @Scheduled(fixedRate = 1000)
    public void monitorAndHedge() {
        for (String botKey : activeBots.keySet()) {
            ActiveTrade trade = activeBots.get(botKey);

            try {
                // 1. 국내 거래소에서 실제 체결량 조회 (주문 상태 API 호출)
                // double currentFilled = domesticClient.getFilledQty(trade.getDomesticOrderId());
                double currentFilled = trade.getFilledQty() + (trade.getTotalTargetQty() * 0.1); // 시뮬레이션: 10%씩 체결된다고 가정
                if (currentFilled > trade.getTotalTargetQty()) currentFilled = trade.getTotalTargetQty();

                trade.setFilledQty(currentFilled);

                // 2. 헷지 필요량 계산 (체결량 - 이미 헷지한 양)
                double gap = trade.getFilledQty() - trade.getHedgedQty();

                if (gap > 0) {
                    log.info(">>> [HEDGE] {} 체결 감지: {} qty. 해외 거래소({}) 숏 주문 실행.",
                            botKey, gap, trade.getRequest().getForeignExchange());

                    // 해외 거래소에 gap만큼 시장가 숏 주문 (바이낸스/바이비트)
                    // foreignClient.openShort(trade.getRequest().getSymbol(), gap, trade.getRequest().getLeverage());

                    trade.setHedgedQty(trade.getHedgedQty() + gap);
                }

                // 3. 완료 체크
                if (trade.getHedgedQty() >= trade.getTotalTargetQty()) {
                    log.info(">>> {} 모든 수량 체결 및 헷지 완료. 봇을 종료합니다.", botKey);
                    activeBots.remove(botKey);
                }

            } catch (Exception e) {
                log.error("{} 모니터링 중 에러: {}", botKey, e.getMessage());
            }
        }
    }

    private String stopArbitrage(String botKey) {
        ActiveTrade trade = activeBots.get(botKey);
        if (trade == null) return "실행 중인 봇이 없습니다.";

        try {
            log.info(">>> 아비트리지 강제 중단 및 남은 주문 취소: {}", botKey);
            // 1. 국내 미체결 주문 취소
            // domesticClient.cancelOrder(trade.getDomesticOrderId());

            // 2. (선택사항) 이미 잡힌 포지션 청산 로직 추가 가능

            activeBots.remove(botKey);
            return botKey + " 봇이 중단되었습니다.";
        } catch (Exception e) {
            return "중단 중 오류: " + e.getMessage();
        }
    }

    public Map<String, Boolean> getBotStatus() {
        Map<String, Boolean> status = new ConcurrentHashMap<>();
        activeBots.forEach((key, val) -> status.put(key, true));
        return status;
    }
}