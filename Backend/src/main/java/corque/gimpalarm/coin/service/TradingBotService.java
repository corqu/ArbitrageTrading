package corque.gimpalarm.coin.service;

import corque.gimpalarm.coin.dto.KimpResponseDto;
import corque.gimpalarm.coin.dto.PriceChangedEvent;
import corque.gimpalarm.coin.dto.PriceManager;
import corque.gimpalarm.coin.dto.TradingRequest;
import corque.gimpalarm.user.domain.User;
import corque.gimpalarm.user.repository.UserCredentialRepository;
import corque.gimpalarm.user.repository.UserRepository;
import corque.gimpalarm.user.service.ExchangeApiService;
import corque.gimpalarm.userbot.domain.UserBot;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class TradingBotService {

    private final KimpService kimpService;
    private final UserCredentialRepository userCredentialRepository;
    private final UserRepository userRepository;
    private final ExchangeApiService exchangeApiService;
    private final PriceManager priceManager;

    public enum BotStatus {
        WAITING,    // 진입 대기
        ENTERING,   // 지정가 주문 후 체결 대기 (30초)
        HOLDING,    // 포지션 유지
        EXITING     // 청산 중
    }

    @Data
    @AllArgsConstructor
    private static class ActiveTrade {
        private TradingRequest request;
        private BotStatus status;
        private Long userId;
        private String domesticOrderId;
        private String foreignOrderId;
        private double totalTargetQty;
        private double filledQty;
        private double hedgedQty;
        private Double slPrice;
        private Double tpPrice;
        private LocalDateTime entryTime;
    }

    private final Map<String, ActiveTrade> activeBots = new ConcurrentHashMap<>();

    public void loadActiveBots(List<UserBot> activeUserBots) {
        for (UserBot bot : activeUserBots) {
            TradingRequest request = new TradingRequest();
            request.setSymbol(bot.getSymbol());
            request.setDomesticExchange(bot.getDomesticExchange());
            request.setForeignExchange(bot.getForeignExchange());
            request.setAmountKrw(bot.getAmountKrw());
            request.setLeverage(bot.getLeverage());
            request.setAction(bot.getAction());
            request.setEntryKimp(bot.getEntryKimp());
            request.setExitKimp(bot.getExitKimp());
            request.setStopLossPercent(bot.getStopLossPercent());
            request.setTakeProfitPercent(bot.getTakeProfitPercent());

            String botKey = generateBotKey(bot.getUser().getId(), request);
            activeBots.put(botKey, new ActiveTrade(request, BotStatus.WAITING, bot.getUser().getId(), null, null, 0.0, 0.0, 0.0, null, null, null));
        }
    }

    private String generateBotKey(Long userId, TradingRequest request) {
        return String.format("%d:%s:%s:%s", userId, request.getSymbol().toUpperCase(),
                request.getDomesticExchange().toUpperCase(), request.getForeignExchange().toUpperCase());
    }

    public String executeTradeForUser(Long userId, TradingRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("No User"));
        String botKey = generateBotKey(user.getId(), request);
        if ("STOP".equalsIgnoreCase(request.getAction())) return stopArbitrage(botKey);
        
        activeBots.put(botKey, new ActiveTrade(request, BotStatus.WAITING, user.getId(), null, null, 0.0, 0.0, 0.0, null, null, null));
        return botKey + " 지정가 자동매매 감시 시작.";
    }

    @EventListener
    public void onPriceChanged(PriceChangedEvent event) {
        if (activeBots.isEmpty()) return;
        String symbol = event.getKey().contains("_") ? event.getKey().split("_")[event.getKey().split("_").length - 1] : event.getKey();

        activeBots.forEach((key, trade) -> {
            if (trade.getRequest().getSymbol().equalsIgnoreCase(symbol)) {
                if (trade.getStatus() == BotStatus.WAITING || trade.getStatus() == BotStatus.HOLDING) {
                    checkConditionAndExecute(key, trade);
                }
                if (trade.getStatus() == BotStatus.HOLDING && event.getKey().startsWith("BN_F_")) {
                    checkSlTpCondition(key, trade, event.getPrice());
                }
            }
        });
    }

    private void checkConditionAndExecute(String botKey, ActiveTrade trade) {
        Map<String, List<KimpResponseDto>> allKimp = kimpService.calculateAllPairs();
        if (trade.getStatus() == BotStatus.WAITING) checkEntryCondition(botKey, trade, allKimp);
        else if (trade.getStatus() == BotStatus.HOLDING) checkExitCondition(botKey, trade, allKimp);
    }

    private void checkEntryCondition(String botKey, ActiveTrade trade, Map<String, List<KimpResponseDto>> allKimp) {
        String pairKey = trade.getRequest().getDomesticExchange().toLowerCase().substring(0, 2) + "-" + 
                         trade.getRequest().getForeignExchange().toLowerCase().substring(0, 2);
        List<KimpResponseDto> list = allKimp.get(pairKey);
        if (list == null) return;

        list.stream().filter(k -> k.getSymbol().equalsIgnoreCase(trade.getRequest().getSymbol())).findFirst().ifPresent(k -> {
            if (k.getRatio() <= trade.getRequest().getEntryKimp()) {
                log.info(">>> [MATCH] {} 진입 조건 충족. 지정가 양방향 주문 실행", botKey);
                try {
                    String symbol = trade.getRequest().getSymbol();
                    String domesticEx = trade.getRequest().getDomesticExchange().toUpperCase();
                    
                    // 1. 국내/해외 현재가 가져오기
                    Double dPrice = priceManager.getPrice(("BITHUMB".equals(domesticEx) ? "BT_" : "UB_") + symbol);
                    Double fPrice = priceManager.getPrice("BN_F_" + symbol);
                    
                    if (dPrice == null || fPrice == null) return;

                    // 2. 수량 계산 (국내 기준)
                    double qty = trade.getRequest().getAmountKrw() / dPrice;
                    trade.setTotalTargetQty(qty);

                    // 3. 양방향 지정가 주문 (Limit)
                    Map<String, Object> dRes = "BITHUMB".equals(domesticEx) ?
                        exchangeApiService.orderBithumb(trade.getUserId(), symbol, "bid", qty, dPrice, "limit") :
                        exchangeApiService.orderUpbit(trade.getUserId(), symbol, "bid", qty, dPrice, "limit");
                    
                    exchangeApiService.setBinanceLeverage(trade.getUserId(), symbol, trade.getRequest().getLeverage());
                    Map<String, Object> fRes = exchangeApiService.orderBinanceFutures(trade.getUserId(), symbol, "SELL", "SHORT", qty, fPrice, "LIMIT");

                    trade.setDomesticOrderId((String) dRes.get("uuid"));
                    trade.setForeignOrderId(fRes.get("orderId").toString());
                    trade.setEntryTime(LocalDateTime.now());
                    trade.setStatus(BotStatus.ENTERING);
                    
                } catch (Exception e) { log.error("Entry Error: {}", e.getMessage()); activeBots.remove(botKey); }
            }
        });
    }

    @Scheduled(fixedRate = 1000)
    public void processOngoingTrades() {
        activeBots.forEach((key, trade) -> {
            if (trade.getStatus() == BotStatus.ENTERING) {
                // 30초 대기 로직
                if (ChronoUnit.SECONDS.between(trade.getEntryTime(), LocalDateTime.now()) >= 30) {
                    balancePositions(key, trade);
                }
            } else if (trade.getStatus() == BotStatus.HOLDING) {
                checkForeignPositionAlive(key, trade);
            }
        });
    }

    private void balancePositions(String botKey, ActiveTrade trade) {
        log.info(">>> [BALANCE] 30초 경과. 미체결 취소 및 수량 동기화 시작: {}", botKey);
        try {
            String symbol = trade.getRequest().getSymbol();
            String domesticEx = trade.getRequest().getDomesticExchange().toUpperCase();

            // 1. 미체결 주문 취소
            if ("BITHUMB".equals(domesticEx)) { /* 빗썸 취소 미구현시 패스 */ } 
            else exchangeApiService.cancelOrderUpbit(trade.getUserId(), trade.domesticOrderId);
            exchangeApiService.cancelOrderBinance(trade.getUserId(), symbol, trade.foreignOrderId);

            // 2. 실제 체결 수량 조회
            double dFilled = 0;
            if (!"BITHUMB".equals(domesticEx)) {
                Map<String, Object> dInfo = exchangeApiService.getOrderUpbit(trade.getUserId(), trade.domesticOrderId);
                dFilled = Double.parseDouble(dInfo.get("executed_volume").toString());
            }
            Map<String, Object> fInfo = exchangeApiService.getOrderBinance(trade.getUserId(), symbol, trade.foreignOrderId);
            double fFilled = Double.parseDouble(fInfo.get("executedQty").toString());

            // 3. 더 적게 체결된 쪽을 기준으로 맞춤
            double minQty = Math.min(dFilled, fFilled);
            log.info(">>> [RESULT] 체결량 - 국내: {}, 해외: {} -> 목표: {}", dFilled, fFilled, minQty);

            if (minQty <= 0) {
                log.warn(">>> 체결량이 없어 봇을 종료합니다.");
                activeBots.remove(botKey);
                return;
            }

            // 4. 초과분 정리 (시장가)
            if (dFilled > minQty) {
                if ("BITHUMB".equals(domesticEx)) exchangeApiService.orderBithumb(trade.getUserId(), symbol, "ask", dFilled - minQty, null, "market");
                else exchangeApiService.orderUpbit(trade.getUserId(), symbol, "ask", dFilled - minQty, null, "market");
            }
            if (fFilled > minQty) {
                exchangeApiService.orderBinanceFutures(trade.getUserId(), symbol, "BUY", "SHORT", fFilled - minQty, null, "MARKET");
            }

            trade.setFilledQty(minQty);
            trade.setHedgedQty(minQty);
            
            // SL/TP 설정 (해외 체결가 기준)
            double avgPrice = Double.parseDouble(fInfo.get("avgPrice").toString());
            calculateAndSetSlTp(trade, avgPrice);
            
            trade.setStatus(BotStatus.HOLDING);
            log.info(">>> [HOLD] 수량 동기화 완료 및 유지 단계 진입: {}", minQty);

        } catch (Exception e) { log.error("Balance Error: {}", e.getMessage()); }
    }

    private void calculateAndSetSlTp(ActiveTrade trade, double entryPrice) {
        int lev = trade.getRequest().getLeverage();
        if (trade.getRequest().getStopLossPercent() != null) {
            trade.setSlPrice(entryPrice * (1 + (trade.getRequest().getStopLossPercent() / 100.0 / lev)));
            exchangeApiService.orderBinanceFuturesConditional(trade.getUserId(), trade.getRequest().getSymbol(), "BUY", "SHORT", trade.getHedgedQty(), trade.getSlPrice(), "STOP_MARKET");
        }
        if (trade.getRequest().getTakeProfitPercent() != null) {
            trade.setTpPrice(entryPrice * (1 - (trade.getRequest().getTakeProfitPercent() / 100.0 / lev)));
            exchangeApiService.orderBinanceFuturesConditional(trade.getUserId(), trade.getRequest().getSymbol(), "BUY", "SHORT", trade.getHedgedQty(), trade.getTpPrice(), "TAKE_PROFIT_MARKET");
        }
    }

    private void checkExitCondition(String botKey, ActiveTrade trade, Map<String, List<KimpResponseDto>> allKimp) {
        String pairKey = trade.getRequest().getDomesticExchange().toLowerCase().substring(0, 2) + "-" + 
                         trade.getRequest().getForeignExchange().toLowerCase().substring(0, 2);
        List<KimpResponseDto> list = allKimp.get(pairKey);
        if (list == null) return;

        list.stream().filter(k -> k.getSymbol().equalsIgnoreCase(trade.getRequest().getSymbol())).findFirst().ifPresent(k -> {
            if (k.getRatio() >= trade.getRequest().getExitKimp()) {
                log.info(">>> [EXIT] 탈출 조건 충족. 시장가 전량 청산 시작!");
                triggerMarketExit(botKey, trade);
            }
        });
    }

    private void triggerMarketExit(String botKey, ActiveTrade trade) {
        try {
            String symbol = trade.getRequest().getSymbol();
            String ex = trade.getRequest().getDomesticExchange().toUpperCase();
            if ("BITHUMB".equals(ex)) exchangeApiService.orderBithumb(trade.getUserId(), symbol, "ask", trade.getFilledQty(), null, "market");
            else exchangeApiService.orderUpbit(trade.getUserId(), symbol, "ask", trade.getFilledQty(), null, "market");
            
            exchangeApiService.orderBinanceFutures(trade.getUserId(), symbol, "BUY", "SHORT", trade.getHedgedQty(), null, "MARKET");
            activeBots.remove(botKey);
        } catch (Exception e) { log.error("Exit Error: {}", e.getMessage()); }
    }

    private void checkSlTpCondition(String botKey, ActiveTrade trade, double currentPrice) {
        boolean trigger = (trade.getSlPrice() != null && currentPrice >= trade.getSlPrice()) ||
                          (trade.getTpPrice() != null && currentPrice <= trade.getTpPrice());
        if (trigger) triggerMarketExit(botKey, trade);
    }

    private void checkForeignPositionAlive(String botKey, ActiveTrade trade) {
        double pos = exchangeApiService.getBinanceFuturesPosition(trade.getUserId(), trade.getRequest().getSymbol());
        if (pos == 0.0) {
            log.warn(">>> [SYSTEM] 해외 포지션 종료 감지. 국내 정리 실행");
            String ex = trade.getRequest().getDomesticExchange().toUpperCase();
            if ("BITHUMB".equals(ex)) exchangeApiService.orderBithumb(trade.getUserId(), trade.getRequest().getSymbol(), "ask", trade.getFilledQty(), null, "market");
            else exchangeApiService.orderUpbit(trade.getUserId(), trade.getRequest().getSymbol(), "ask", trade.getFilledQty(), null, "market");
            activeBots.remove(botKey);
        }
    }

    private String stopArbitrage(String botKey) {
        activeBots.remove(botKey);
        return botKey + " 중단됨.";
    }

    public Map<String, Boolean> getBotStatus() {
        Map<String, Boolean> status = new ConcurrentHashMap<>();
        activeBots.forEach((k, v) -> status.put(k, true));
        return status;
    }
}
