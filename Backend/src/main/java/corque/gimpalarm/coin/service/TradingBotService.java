package corque.gimpalarm.coin.service;

import corque.gimpalarm.coin.dto.KimpResponseDto;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
        private Long userId;
        private String domesticOrderId;
        private double totalTargetQty;
        private double filledQty;
        private double hedgedQty;
        private Double slPrice;
        private Double tpPrice;
    }

    private final Map<String, ActiveTrade> activeBots = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info(">>> [SYSTEM] TradingBotService 초기화 완료");
    }

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
            activeBots.put(botKey, new ActiveTrade(request, BotStatus.WAITING, bot.getUser().getId(), null, 0.0, 0.0, 0.0, null, null));
            log.info(">>> [SYSTEM] 활성 봇 복구: {}", botKey);
        }
    }

    private String generateBotKey(Long userId, TradingRequest request) {
        return String.format("%d:%s:%s:%s",
                userId,
                request.getSymbol().toUpperCase(),
                request.getDomesticExchange().toUpperCase(),
                request.getForeignExchange().toUpperCase());
    }

    public String executeTrade(TradingRequest request) {
        org.springframework.security.core.Authentication auth = 
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return "로그인이 필요한 서비스입니다.";
        }

        User user = userRepository.findByEmail(auth.getName())
            .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        return executeTradeForUser(user, request);
    }

    public String executeTradeForUser(User user, TradingRequest request) {
        if (!hasCredentials(user, request.getDomesticExchange()) || !hasCredentials(user, request.getForeignExchange())) {
            return String.format("%s 또는 %s API 키가 등록되지 않았습니다.", 
                request.getDomesticExchange(), request.getForeignExchange());
        }

        String botKey = generateBotKey(user.getId(), request);

        if ("STOP".equalsIgnoreCase(request.getAction())) {
            return stopArbitrage(botKey);
        }
        
        return startAutoArbitrage(request, botKey, user.getId());
    }

    private boolean hasCredentials(User user, String exchange) {
        String ex = exchange.toUpperCase();
        if (ex.contains("BINANCE")) ex = "BINANCE";
        if (ex.contains("BYBIT")) ex = "BYBIT";
        return userCredentialRepository.findByUserAndExchange(user, ex).isPresent();
    }

    private String startAutoArbitrage(TradingRequest request, String botKey, Long userId) {
        if (activeBots.containsKey(botKey)) return botKey + " 봇이 이미 실행 중입니다.";
        activeBots.put(botKey, new ActiveTrade(request, BotStatus.WAITING, userId, null, 0.0, 0.0, 0.0, null, null));
        return botKey + " 자동 매매 구독 완료.";
    }

    @EventListener
    public void onPriceChanged(corque.gimpalarm.coin.dto.PriceChangedEvent event) {
        if (activeBots.isEmpty()) return;
        String key = event.getKey();
        String symbol = key.contains("_") ? key.split("_")[key.split("_").length - 1] : key;

        activeBots.entrySet().stream()
            .filter(e -> e.getValue().getRequest().getSymbol().equalsIgnoreCase(symbol))
            .forEach(e -> {
                ActiveTrade trade = e.getValue();
                if (trade.getStatus() == BotStatus.WAITING || trade.getStatus() == BotStatus.HOLDING) {
                    checkConditionAndExecute(e.getKey(), trade);
                }
                if (trade.getStatus() == BotStatus.HOLDING && key.startsWith("BN_F_")) {
                    checkSlTpCondition(e.getKey(), trade, event.getPrice());
                }
            });
    }

    private void checkConditionAndExecute(String botKey, ActiveTrade trade) {
        Map<String, List<KimpResponseDto>> allKimp = kimpService.calculateAllPairs();
        if (trade.getStatus() == BotStatus.WAITING) checkEntryCondition(botKey, trade, allKimp);
        else if (trade.getStatus() == BotStatus.HOLDING) checkExitCondition(botKey, trade, allKimp);
    }

    private void checkSlTpCondition(String botKey, ActiveTrade trade, double currentPrice) {
        boolean trigger = false;
        if (trade.getSlPrice() != null && currentPrice >= trade.getSlPrice()) trigger = true;
        else if (trade.getTpPrice() != null && currentPrice <= trade.getTpPrice()) trigger = true;

        if (trigger) triggerMarketExit(botKey, trade);
    }

    private void triggerMarketExit(String botKey, ActiveTrade trade) {
        try {
            String ex = trade.getRequest().getDomesticExchange().toUpperCase();
            if ("BITHUMB".equals(ex)) exchangeApiService.orderBithumb(trade.getUserId(), trade.getRequest().getSymbol(), "ask", trade.getFilledQty(), null, "market");
            else exchangeApiService.orderUpbit(trade.getUserId(), trade.getRequest().getSymbol(), "ask", trade.getFilledQty(), null, "market");
            trade.setStatus(BotStatus.EXITING);
        } catch (Exception e) { log.error("Exit Error: {}", e.getMessage()); }
    }

    private void checkEntryCondition(String botKey, ActiveTrade trade, Map<String, List<KimpResponseDto>> allKimp) {
        String pairKey = trade.getRequest().getDomesticExchange().toLowerCase().substring(0, 2) + "-" + 
                         trade.getRequest().getForeignExchange().toLowerCase().substring(0, 2);
        List<KimpResponseDto> list = allKimp.get(pairKey);
        if (list == null) return;

        list.stream().filter(k -> k.getSymbol().equalsIgnoreCase(trade.getRequest().getSymbol())).findFirst().ifPresent(k -> {
            if (k.getRatio() <= trade.getRequest().getEntryKimp()) {
                try {
                    String ex = trade.getRequest().getDomesticExchange().toUpperCase();
                    Map<String, Object> res = "BITHUMB".equals(ex) ? 
                        exchangeApiService.orderBithumb(trade.getUserId(), trade.getRequest().getSymbol(), "bid", 0, trade.getRequest().getAmountKrw(), "price") :
                        exchangeApiService.orderUpbit(trade.getUserId(), trade.getRequest().getSymbol(), "bid", 0, trade.getRequest().getAmountKrw(), "price");
                    trade.setDomesticOrderId((String) res.get("uuid"));
                    trade.setStatus(BotStatus.ENTERING);
                } catch (Exception e) { log.error("Entry Error: {}", e.getMessage()); activeBots.remove(botKey); }
            }
        });
    }

    @org.springframework.scheduling.annotation.Async
    protected void handleEnteringProcess(String botKey, ActiveTrade trade) {
        try {
            String ex = trade.getRequest().getDomesticExchange().toUpperCase();
            Double p = priceManager.getPrice(("BITHUMB".equals(ex) ? "BT_" : "UB_") + trade.getRequest().getSymbol());
            if (p == null) return;

            double qty = trade.getRequest().getAmountKrw() / p;
            trade.setTotalTargetQty(qty);
            trade.setFilledQty(qty);

            double gap = qty - trade.getHedgedQty();
            if (gap > 0) {
                exchangeApiService.setBinanceLeverage(trade.getUserId(), trade.getRequest().getSymbol(), trade.getRequest().getLeverage());
                Map<String, Object> res = exchangeApiService.orderBinanceFutures(trade.getUserId(), trade.getRequest().getSymbol(), "SELL", "SHORT", gap, null, "MARKET");
                
                double entryPrice = (res != null && res.containsKey("avgPrice")) ? Double.parseDouble(res.get("avgPrice").toString()) : 
                                   priceManager.getPrice("BN_F_" + trade.getRequest().getSymbol());
                calculateAndSetSlTp(trade, entryPrice);
                trade.setHedgedQty(trade.getHedgedQty() + gap);
            }

            if (trade.getHedgedQty() >= trade.getTotalTargetQty() * 0.99) trade.setStatus(BotStatus.HOLDING);
        } catch (Exception e) { log.error("Hedge Error: {}", e.getMessage()); }
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
                try {
                    String ex = trade.getRequest().getDomesticExchange().toUpperCase();
                    if ("BITHUMB".equals(ex)) exchangeApiService.orderBithumb(trade.getUserId(), trade.getRequest().getSymbol(), "ask", trade.getFilledQty(), null, "market");
                    else exchangeApiService.orderUpbit(trade.getUserId(), trade.getRequest().getSymbol(), "ask", trade.getFilledQty(), null, "market");
                    trade.setStatus(BotStatus.EXITING);
                } catch (Exception e) { log.error("Exit Condition Error: {}", e.getMessage()); }
            }
        });
    }

    @org.springframework.scheduling.annotation.Async
    protected void handleExitingProcess(String botKey, ActiveTrade trade) {
        try {
            exchangeApiService.orderBinanceFutures(trade.getUserId(), trade.getRequest().getSymbol(), "BUY", "SHORT", trade.getHedgedQty(), null, "MARKET");
            activeBots.remove(botKey);
        } catch (Exception e) { log.error("Exiting Process Error: {}", e.getMessage()); }
    }

    @Scheduled(fixedRate = 1000)
    public void processOngoingTrades() {
        activeBots.forEach((k, t) -> {
            if (t.getStatus() == BotStatus.ENTERING) handleEnteringProcess(k, t);
            else if (t.getStatus() == BotStatus.EXITING) handleExitingProcess(k, t);
            else if (t.getStatus() == BotStatus.HOLDING) checkForeignPositionAlive(k, t);
        });
    }

    private void checkForeignPositionAlive(String botKey, ActiveTrade trade) {
        double pos = exchangeApiService.getBinanceFuturesPosition(trade.getUserId(), trade.getRequest().getSymbol());
        if (pos == 0.0) triggerDomesticExitOnly(botKey, trade);
    }

    private void triggerDomesticExitOnly(String botKey, ActiveTrade trade) {
        try {
            String ex = trade.getRequest().getDomesticExchange().toUpperCase();
            if ("BITHUMB".equals(ex)) exchangeApiService.orderBithumb(trade.getUserId(), trade.getRequest().getSymbol(), "ask", trade.getFilledQty(), null, "market");
            else exchangeApiService.orderUpbit(trade.getUserId(), trade.getRequest().getSymbol(), "ask", trade.getFilledQty(), null, "market");
            activeBots.remove(botKey);
        } catch (Exception e) { log.error("Domestic Exit Error: {}", e.getMessage()); }
    }

    private String stopArbitrage(String botKey) {
        if (activeBots.containsKey(botKey)) {
            activeBots.remove(botKey);
            return botKey + " 중단됨.";
        }
        return "봇 없음.";
    }

    public Map<String, Boolean> getBotStatus() {
        Map<String, Boolean> status = new ConcurrentHashMap<>();
        activeBots.forEach((k, v) -> status.put(k, true));
        return status;
    }
}
