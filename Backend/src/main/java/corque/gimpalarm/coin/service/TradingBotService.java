package corque.gimpalarm.coin.service;

import corque.gimpalarm.coin.dto.KimpResponseDto;
import corque.gimpalarm.coin.dto.TradingRequest;
import corque.gimpalarm.common.config.CoinConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class TradingBotService {

    private final CoinConfig coinConfig;
    private final KimpService kimpService;
    private final corque.gimpalarm.user.repository.UserCredentialRepository userCredentialRepository;
    private final corque.gimpalarm.user.repository.UserRepository userRepository;

    // 봇의 상태 정의
    public enum BotStatus {
        WAITING,    // 진입 김프 대기 중
        ENTERING,   // 국내 매수 중 (체결 감시)
        HOLDING,    // 탈출 김프 대기 중 (숏 헷지 완료 상태)
        EXITING     // 탈출 진행 중 (국내 매도 + 숏 청산)
    }

    @Data
    @AllArgsConstructor
    private static class ActiveTrade {
        private TradingRequest request;
        private BotStatus status;
        private Long userId; // 사용자 ID 추가
        private String domesticOrderId;
        private double totalTargetQty;
        private double filledQty;
        private double hedgedQty;
    }

    private final Map<String, ActiveTrade> activeBots = new ConcurrentHashMap<>();

    public String executeTrade(TradingRequest request) {
        // 1. 현재 사용자 인증 정보 가져오기
        org.springframework.security.core.Authentication auth = 
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return "로그인이 필요한 서비스입니다.";
        }

        corque.gimpalarm.user.domain.User user = userRepository.findByEmail(auth.getName())
            .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        // 2. 해당 거래소들의 API 자격 증명이 있는지 확인
        if (!hasCredentials(user, request.getDomesticExchange()) || !hasCredentials(user, request.getForeignExchange())) {
            return String.format("%s 또는 %s API 키가 등록되지 않았습니다.", 
                request.getDomesticExchange(), request.getForeignExchange());
        }

        String botKey = String.format("%s:%s:%s",
                request.getSymbol().toUpperCase(),
                request.getDomesticExchange().toUpperCase(),
                request.getForeignExchange().toUpperCase());

        if ("START_AUTO".equalsIgnoreCase(request.getAction())) {
            return startAutoArbitrage(request, botKey, user.getId());
        } else if ("START".equalsIgnoreCase(request.getAction())) {
            return startLimitArbitrage(request, botKey, user.getId());
        } else if ("STOP".equalsIgnoreCase(request.getAction())) {
            return stopArbitrage(botKey);
        }
        return "알 수 없는 명령입니다.";
    }

    private boolean hasCredentials(corque.gimpalarm.user.domain.User user, String exchange) {
        // 실제 거래소 이름 매칭 (BINANCE_FUTURES 등은 BINANCE로 통일)
        String ex = exchange.toUpperCase();
        if (ex.contains("BINANCE")) ex = "BINANCE";
        if (ex.contains("BYBIT")) ex = "BYBIT";
        
        return userCredentialRepository.findByUserAndExchange(user, ex).isPresent();
    }

    private String startAutoArbitrage(TradingRequest request, String botKey, Long userId) {
        if (activeBots.containsKey(botKey)) {
            return botKey + " 봇이 이미 실행 중입니다.";
        }
        
        log.info(">>> [USER:{}] 자동 아비트리지 감시 시작: {} (진입: {}%, 탈출: {}%)", 
                userId, botKey, request.getEntryKimp(), request.getExitKimp());
        
        activeBots.put(botKey, new ActiveTrade(request, BotStatus.WAITING, userId, null, 0.0, 0.0, 0.0));
        return botKey + " 자동 매매 구독 완료. 목표 김프 도달 시 등록된 API 키로 자동으로 진입합니다.";
    }

    private String startLimitArbitrage(TradingRequest request, String botKey, Long userId) {
        if (activeBots.containsKey(botKey)) {
            return botKey + " 봇이 이미 실행 중입니다.";
        }

        log.info(">>> [USER:{}] 지정가 아비트리지 진입: {} @ {} KRW", userId, botKey, request.getLimitPrice());

        try {
            double targetQty = request.getAmountKrw() / request.getLimitPrice();
            String orderId = "ORD-" + System.currentTimeMillis();

            activeBots.put(botKey, new ActiveTrade(request, BotStatus.ENTERING, userId, orderId, targetQty, 0.0, 0.0));
            return botKey + " 지정가 주문 완료. 체결 감시 및 헷지를 시작합니다.";
        } catch (Exception e) {
            return "오류 발생: " + e.getMessage();
        }
    }

    @Scheduled(fixedRate = 1000)
    public void monitorAndHedge() {
        if (activeBots.isEmpty()) return;

        // 실시간 모든 페어의 김프 가져오기
        Map<String, List<KimpResponseDto>> allKimp = kimpService.calculateAllPairs();

        for (String botKey : activeBots.keySet()) {
            ActiveTrade trade = activeBots.get(botKey);
            String symbol = trade.getRequest().getSymbol();

            try {
                switch (trade.getStatus()) {
                    case WAITING:
                        checkEntryCondition(botKey, trade, allKimp);
                        break;
                    case ENTERING:
                        handleEnteringProcess(botKey, trade);
                        break;
                    case HOLDING:
                        checkExitCondition(botKey, trade, allKimp);
                        break;
                    case EXITING:
                        handleExitingProcess(botKey, trade);
                        break;
                }
            } catch (Exception e) {
                log.error("{} 모니터링 중 에러: {}", botKey, e.getMessage());
            }
        }
    }

    private void checkEntryCondition(String botKey, ActiveTrade trade, Map<String, List<KimpResponseDto>> allKimp) {
        String pairKey = trade.getRequest().getDomesticExchange().toLowerCase().substring(0, 2) + "-" + 
                         trade.getRequest().getForeignExchange().toLowerCase().substring(0, 2);
        
        List<KimpResponseDto> kimpList = allKimp.get(pairKey);
        if (kimpList == null) return;

        kimpList.stream()
            .filter(k -> k.getSymbol().equalsIgnoreCase(trade.getRequest().getSymbol()))
            .findFirst()
            .ifPresent(k -> {
                if (k.getRatio() <= trade.getRequest().getEntryKimp()) {
                    log.info(">>> [MATCH] {} 진입 조건 충족 (현재: {}% <= 목표: {}%). 매수 시작!", 
                            botKey, k.getRatio(), trade.getRequest().getEntryKimp());
                    
                    // 실제로는 시장가 매수 주문 실행
                    trade.setStatus(BotStatus.ENTERING);
                    trade.setTotalTargetQty(trade.getRequest().getAmountKrw() / (k.getRatio() / 100.0 * 1450.0 + 1450.0)); // 대략적 계산
                    trade.setDomesticOrderId("ORD-AUTO-" + System.currentTimeMillis());
                }
            });
    }

    private void handleEnteringProcess(String botKey, ActiveTrade trade) {
        // 시뮬레이션: 매 초마다 20%씩 체결된다고 가정
        double newFilled = trade.getFilledQty() + (trade.getTotalTargetQty() * 0.2);
        if (newFilled > trade.getTotalTargetQty()) newFilled = trade.getTotalTargetQty();
        
        double gap = newFilled - trade.getHedgedQty();
        if (gap > 0) {
            log.info(">>> [HEDGE] {} 국내 체결({}) -> 해외 숏({}) 실행", botKey, gap, gap);
            trade.setHedgedQty(trade.getHedgedQty() + gap);
        }
        
        trade.setFilledQty(newFilled);

        if (trade.getHedgedQty() >= trade.getTotalTargetQty()) {
            log.info(">>> [HOLD] {} 모든 포지션 진입 완료. 탈출 김프({}) 대기 시작.", 
                    botKey, trade.getRequest().getExitKimp());
            trade.setStatus(BotStatus.HOLDING);
        }
    }

    private void checkExitCondition(String botKey, ActiveTrade trade, Map<String, List<KimpResponseDto>> allKimp) {
        String pairKey = trade.getRequest().getDomesticExchange().toLowerCase().substring(0, 2) + "-" + 
                         trade.getRequest().getForeignExchange().toLowerCase().substring(0, 2);
        
        List<KimpResponseDto> kimpList = allKimp.get(pairKey);
        if (kimpList == null) return;

        kimpList.stream()
            .filter(k -> k.getSymbol().equalsIgnoreCase(trade.getRequest().getSymbol()))
            .findFirst()
            .ifPresent(k -> {
                if (k.getRatio() >= trade.getRequest().getExitKimp()) {
                    log.info(">>> [EXIT] {} 탈출 조건 충족 (현재: {}% >= 목표: {}%). 청산 시작!", 
                            botKey, k.getRatio(), trade.getRequest().getExitKimp());
                    trade.setStatus(BotStatus.EXITING);
                }
            });
    }

    private void handleExitingProcess(String botKey, ActiveTrade trade) {
        // 시뮬레이션: 매 초마다 50%씩 청산된다고 가정
        log.info(">>> [CLOSE] {} 포지션 청산 중...", botKey);
        activeBots.remove(botKey);
        log.info(">>> [FINISH] {} 차익거래 사이클 종료!", botKey);
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