package corque.gimpalarm.coin.service;

import corque.gimpalarm.coin.domain.BotStatus;
import corque.gimpalarm.botstate.service.BotTradeStateService;
import corque.gimpalarm.common.exception.BadRequestException;
import corque.gimpalarm.common.exception.NotFoundException;
import corque.gimpalarm.coin.dto.KimpResponseDto;
import corque.gimpalarm.coin.dto.PriceChangedEvent;
import corque.gimpalarm.coin.dto.PriceManager;
import corque.gimpalarm.coin.dto.TradingRequest;
import corque.gimpalarm.tradeorder.service.TradeOrderService;
import corque.gimpalarm.user.domain.User;
import corque.gimpalarm.user.repository.UserRepository;
import corque.gimpalarm.user.service.ExchangeApiService;
import corque.gimpalarm.userbot.domain.UserBot;
import corque.gimpalarm.userbot.domain.UserBotStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class TradingBotService {

    private static final String ENTRY_DOMESTIC = "ENTRY_DOMESTIC";
    private static final String ENTRY_FOREIGN = "ENTRY_FOREIGN";

    private final KimpService kimpService;
    private final UserRepository userRepository;
    private final ExchangeApiService exchangeApiService;
    private final PriceManager priceManager;
    private final TradeOrderService tradeOrderService;
    private final BotTradeStateService botTradeStateService;
    private final BotStatusSyncService botStatusSyncService;

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
    private final Set<String> balancingBots = ConcurrentHashMap.newKeySet();

    public void loadActiveBots(List<UserBot> activeUserBots) {
        for (UserBot bot : activeUserBots) {
            if (bot.getStatus() == UserBotStatus.STOPPED) {
                continue;
            }

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
            BotTradeStateService.RestoredTradeSnapshot restoredTrade = botTradeStateService.restore(bot.getUser().getId(), botKey);
            activeBots.put(botKey, new ActiveTrade(
                    request,
                    restoredTrade.getStatus(),
                    restoredTrade.getUserId(),
                    restoredTrade.getDomesticOrderId(),
                    restoredTrade.getForeignOrderId(),
                    restoredTrade.getTotalTargetQty(),
                    restoredTrade.getFilledQty(),
                    restoredTrade.getHedgedQty(),
                    restoredTrade.getSlPrice(),
                    restoredTrade.getTpPrice(),
                    restoredTrade.getEntryTime()
            ));
            botStatusSyncService.sync(bot.getUser().getId(), request, botKey, restoredTrade.getStatus());
        }
    }

    private String generateBotKey(Long userId, TradingRequest request) {
        return String.format("%d:%s:%s:%s", userId, request.getSymbol().toUpperCase(),
                request.getDomesticExchange().toUpperCase(), request.getForeignExchange().toUpperCase());
    }

    public String executeTradeForUser(Long userId, TradingRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        String botKey = generateBotKey(user.getId(), request);
        if ("STOP".equalsIgnoreCase(request.getAction())) {
            botStatusSyncService.sync(userId, request, botKey, BotStatus.STOPPED);
            return stopArbitrage(botKey);
        }

        activeBots.put(botKey, new ActiveTrade(request, BotStatus.WAITING, user.getId(), null, null, 0.0, 0.0, 0.0, null, null, null));
        botStatusSyncService.sync(userId, request, botKey, BotStatus.WAITING);
        return botKey + " trading started";
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
                if (trade.getStatus() == BotStatus.ENTERING) {
                    checkEnteringCancellationCondition(key, trade);
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

    private void checkEnteringCancellationCondition(String botKey, ActiveTrade trade) {
        Map<String, List<KimpResponseDto>> allKimp = kimpService.calculateAllPairs();
        String pairKey = resolvePairKey(trade.getRequest());
        List<KimpResponseDto> list = allKimp.get(pairKey);
        if (list == null) {
            return;
        }

        list.stream()
                .filter(k -> k.getSymbol().equalsIgnoreCase(trade.getRequest().getSymbol()))
                .findFirst()
                .ifPresent(k -> {
                    if (trade.getEntryTime() != null && ChronoUnit.SECONDS.between(trade.getEntryTime(), LocalDateTime.now()) >= 60 && k.getRatio() > trade.getRequest().getEntryKimp()) {
                        log.info(">>> [ENTERING-CANCEL] {} entry kimp recovered to {}. Start balance now.", botKey, k.getRatio());
                        balancePositions(botKey, trade);
                    }
                });
    }

    String resolvePairKey(TradingRequest request) {
        return resolveDomesticCode(request.getDomesticExchange()) + "-" + resolveForeignCode(request.getForeignExchange());
    }

    private String resolveDomesticCode(String domesticExchange) {
        if (domesticExchange == null) {
            throw new BadRequestException("Domestic exchange is required");
        }

        return switch (domesticExchange.trim().toUpperCase()) {
            case "UPBIT" -> "ub";
            case "BITHUMB" -> "bt";
            default -> throw new BadRequestException("Unsupported domestic exchange: " + domesticExchange);
        };
    }

    private String resolveForeignCode(String foreignExchange) {
        if (foreignExchange == null) {
            throw new BadRequestException("Foreign exchange is required");
        }

        return switch (foreignExchange.trim().toUpperCase()) {
            case "BINANCE", "BINANCE_FUTURES" -> "bn";
            case "BYBIT", "BYBIT_FUTURES" -> "bb";
            default -> throw new BadRequestException("Unsupported foreign exchange: " + foreignExchange);
        };
    }

    private void checkEntryCondition(String botKey, ActiveTrade trade, Map<String, List<KimpResponseDto>> allKimp) {
        String pairKey = resolvePairKey(trade.getRequest());
        List<KimpResponseDto> list = allKimp.get(pairKey);
        if (list == null) return;

        list.stream().filter(k -> k.getSymbol().equalsIgnoreCase(trade.getRequest().getSymbol())).findFirst().ifPresent(k -> {
            if (k.getRatio() <= trade.getRequest().getEntryKimp()) {
                log.info(">>> [MATCH] {} entry condition met", botKey);
                try {
                    String symbol = trade.getRequest().getSymbol();
                    String domesticEx = trade.getRequest().getDomesticExchange().toUpperCase();

                    Double dPrice = priceManager.getPrice(("BITHUMB".equals(domesticEx) ? "BT_" : "UB_") + symbol);
                    Double fPrice = priceManager.getPrice("BN_F_" + symbol);

                    if (dPrice == null || fPrice == null) return;

                    double qty = trade.getRequest().getAmountKrw() / dPrice;
                    trade.setTotalTargetQty(qty);

                    Map<String, Object> dRes = "BITHUMB".equals(domesticEx)
                            ? exchangeApiService.orderBithumb(trade.getUserId(), symbol, "bid", qty, dPrice, "limit")
                            : exchangeApiService.orderUpbit(trade.getUserId(), symbol, "bid", qty, dPrice, "limit");
                    tradeOrderService.recordOrder(
                            trade.getUserId(), botKey, domesticEx, "SPOT", ENTRY_DOMESTIC,
                            symbol, "BUY", null, "LIMIT", qty, dPrice, dRes
                    );

                    exchangeApiService.setBinanceLeverage(trade.getUserId(), symbol, trade.getRequest().getLeverage());
                    Map<String, Object> fRes = exchangeApiService.orderBinanceFutures(trade.getUserId(), symbol, "SELL", "SHORT", qty, fPrice, "LIMIT");
                    tradeOrderService.recordOrder(
                            trade.getUserId(), botKey, "BINANCE", "FUTURES", ENTRY_FOREIGN,
                            symbol, "SELL", "SHORT", "LIMIT", qty, fPrice, fRes
                    );

                    trade.setDomesticOrderId(extractDomesticOrderId(dRes));
                    trade.setForeignOrderId(String.valueOf(fRes.get("orderId")));
                    trade.setEntryTime(LocalDateTime.now());
                    trade.setStatus(BotStatus.ENTERING);
                    persistExecutionState(botKey, trade);
                    botStatusSyncService.sync(trade.getUserId(), trade.getRequest(), botKey, BotStatus.ENTERING);
                } catch (Exception e) {
                    log.error("Entry Error: {}", e.getMessage());
                    botStatusSyncService.sync(trade.getUserId(), trade.getRequest(), botKey, BotStatus.ERROR);
                    activeBots.remove(botKey);
                }
            }
        });
    }

    @Scheduled(fixedRate = 1000)
    public void processOngoingTrades() {
        activeBots.forEach((key, trade) -> {
            if (trade.getStatus() == BotStatus.ENTERING) {
                if (trade.getEntryTime() != null && ChronoUnit.SECONDS.between(trade.getEntryTime(), LocalDateTime.now()) >= 60) {
                    balancePositions(key, trade);
                }
            } else if (trade.getStatus() == BotStatus.HOLDING) {
                checkForeignPositionAlive(key, trade);
            }
        });
    }

    private void balancePositions(String botKey, ActiveTrade trade) {
        if (!balancingBots.add(botKey)) {
            log.info(">>> [BALANCE-SKIP] already balancing: {}", botKey);
            return;
        }

        log.info(">>> [BALANCE] synchronize positions: {}", botKey);
        try {
            String symbol = trade.getRequest().getSymbol();
            String domesticEx = trade.getRequest().getDomesticExchange().toUpperCase();

            Map<String, Object> dInfo = "BITHUMB".equals(domesticEx)
                    ? exchangeApiService.getOrderBithumb(trade.getUserId(), trade.getDomesticOrderId())
                    : exchangeApiService.getOrderUpbit(trade.getUserId(), trade.getDomesticOrderId());
            Map<String, Object> fInfo = exchangeApiService.getOrderBinance(trade.getUserId(), symbol, trade.getForeignOrderId());

            tradeOrderService.updateOrderFromExchange(domesticEx, trade.getDomesticOrderId(), dInfo);
            tradeOrderService.updateOrderFromExchange("BINANCE", trade.getForeignOrderId(), fInfo);

            boolean domesticFilled = isFilledState(domesticEx, dInfo);
            boolean foreignFilled = isFilledState("BINANCE", fInfo);

            if (!domesticFilled) {
                if ("BITHUMB".equals(domesticEx)) exchangeApiService.cancelOrderBithumb(trade.getUserId(), trade.getDomesticOrderId());
                else exchangeApiService.cancelOrderUpbit(trade.getUserId(), trade.getDomesticOrderId());
                dInfo = "BITHUMB".equals(domesticEx)
                        ? exchangeApiService.getOrderBithumb(trade.getUserId(), trade.getDomesticOrderId())
                        : exchangeApiService.getOrderUpbit(trade.getUserId(), trade.getDomesticOrderId());
                tradeOrderService.updateOrderFromExchange(domesticEx, trade.getDomesticOrderId(), dInfo);
            }

            if (!foreignFilled) {
                exchangeApiService.cancelOrderBinance(trade.getUserId(), symbol, trade.getForeignOrderId());
                fInfo = exchangeApiService.getOrderBinance(trade.getUserId(), symbol, trade.getForeignOrderId());
                tradeOrderService.updateOrderFromExchange("BINANCE", trade.getForeignOrderId(), fInfo);
            }

            double dFilled = parseExecutedQty(domesticEx, dInfo);
            double fFilled = parseExecutedQty("BINANCE", fInfo);
            double minQty = Math.min(dFilled, fFilled);
            log.info(">>> [RESULT] domestic={}, foreign={}, target={}", dFilled, fFilled, minQty);

            if (minQty <= 0) {
                botStatusSyncService.sync(trade.getUserId(), trade.getRequest(), botKey, BotStatus.ERROR);
                activeBots.remove(botKey);
                return;
            }

            if (dFilled > minQty) {
                Map<String, Object> rebalanceDomesticRes = "BITHUMB".equals(domesticEx)
                        ? exchangeApiService.orderBithumb(trade.getUserId(), symbol, "ask", dFilled - minQty, null, "market")
                        : exchangeApiService.orderUpbit(trade.getUserId(), symbol, "ask", dFilled - minQty, null, "market");
                tradeOrderService.recordOrder(
                        trade.getUserId(), botKey, domesticEx, "SPOT", "REBALANCE_DOMESTIC",
                        symbol, "SELL", null, "MARKET", dFilled - minQty, null, rebalanceDomesticRes
                );
            }
            if (fFilled > minQty) {
                Map<String, Object> rebalanceForeignRes = exchangeApiService.orderBinanceFutures(
                        trade.getUserId(), symbol, "BUY", "SHORT", fFilled - minQty, null, "MARKET");
                tradeOrderService.recordOrder(
                        trade.getUserId(), botKey, "BINANCE", "FUTURES", "REBALANCE_FOREIGN",
                        symbol, "BUY", "SHORT", "MARKET", fFilled - minQty, null, rebalanceForeignRes
                );
            }

            trade.setFilledQty(minQty);
            trade.setHedgedQty(minQty);
            double avgPrice = parseAveragePrice("BINANCE", fInfo);
            calculateAndSetSlTp(trade, avgPrice);
            trade.setStatus(BotStatus.HOLDING);
            persistExecutionState(botKey, trade);
            botStatusSyncService.sync(trade.getUserId(), trade.getRequest(), botKey, BotStatus.HOLDING);
            log.info(">>> [HOLD] holding qty={}", minQty);
        } catch (Exception e) {
            log.error("Balance Error: {}", e.getMessage());
            botStatusSyncService.sync(trade.getUserId(), trade.getRequest(), botKey, BotStatus.ERROR);
            activeBots.remove(botKey);
        } finally {
            balancingBots.remove(botKey);
        }
    }

    private void persistExecutionState(String botKey, ActiveTrade trade) {
        botTradeStateService.findByBotKey(botKey).ifPresent(state ->
                botTradeStateService.updateExecution(
                        state,
                        trade.getDomesticOrderId(),
                        trade.getForeignOrderId(),
                        trade.getTotalTargetQty(),
                        trade.getFilledQty(),
                        trade.getHedgedQty(),
                        trade.getSlPrice(),
                        trade.getTpPrice(),
                        trade.getEntryTime()
                )
        );
    }

    private boolean isFilledState(String exchange, Map<String, Object> orderInfo) {
        if (orderInfo == null) {
            return false;
        }
        String status = "BINANCE".equalsIgnoreCase(exchange)
                ? String.valueOf(orderInfo.get("status"))
                : String.valueOf(orderInfo.get("state"));
        return status != null && ("FILLED".equalsIgnoreCase(status) || "done".equalsIgnoreCase(status));
    }

    private double parseExecutedQty(String exchange, Map<String, Object> orderInfo) {
        if (orderInfo == null) {
            return 0.0;
        }
        Object value = "BINANCE".equalsIgnoreCase(exchange) ? orderInfo.get("executedQty") : orderInfo.get("executed_volume");
        return value == null ? 0.0 : Double.parseDouble(String.valueOf(value));
    }

    private double parseAveragePrice(String exchange, Map<String, Object> orderInfo) {
        if (orderInfo == null) {
            return 0.0;
        }
        Object value = "BINANCE".equalsIgnoreCase(exchange) ? orderInfo.get("avgPrice") : orderInfo.get("price");
        return value == null ? 0.0 : Double.parseDouble(String.valueOf(value));
    }

    private void calculateAndSetSlTp(ActiveTrade trade, double entryPrice) {
        int lev = trade.getRequest().getLeverage();
        String botKey = generateBotKey(trade.getUserId(), trade.getRequest());
        if (trade.getRequest().getStopLossPercent() != null) {
            trade.setSlPrice(entryPrice * (1 + (trade.getRequest().getStopLossPercent() / 100.0 / lev)));
            Map<String, Object> slRes = exchangeApiService.orderBinanceFuturesConditional(
                    trade.getUserId(), trade.getRequest().getSymbol(), "BUY", "SHORT", trade.getHedgedQty(), trade.getSlPrice(), "STOP_MARKET");
            tradeOrderService.recordOrder(
                    trade.getUserId(), botKey, "BINANCE", "FUTURES", "STOP_LOSS",
                    trade.getRequest().getSymbol(), "BUY", "SHORT", "STOP_MARKET", trade.getHedgedQty(), trade.getSlPrice(), slRes
            );
        }
        if (trade.getRequest().getTakeProfitPercent() != null) {
            trade.setTpPrice(entryPrice * (1 - (trade.getRequest().getTakeProfitPercent() / 100.0 / lev)));
            Map<String, Object> tpRes = exchangeApiService.orderBinanceFuturesConditional(
                    trade.getUserId(), trade.getRequest().getSymbol(), "BUY", "SHORT", trade.getHedgedQty(), trade.getTpPrice(), "TAKE_PROFIT_MARKET");
            tradeOrderService.recordOrder(
                    trade.getUserId(), botKey, "BINANCE", "FUTURES", "TAKE_PROFIT",
                    trade.getRequest().getSymbol(), "BUY", "SHORT", "TAKE_PROFIT_MARKET", trade.getHedgedQty(), trade.getTpPrice(), tpRes
            );
        }
    }

    private void checkExitCondition(String botKey, ActiveTrade trade, Map<String, List<KimpResponseDto>> allKimp) {
        String pairKey = resolvePairKey(trade.getRequest());
        List<KimpResponseDto> list = allKimp.get(pairKey);
        if (list == null) return;

        list.stream().filter(k -> k.getSymbol().equalsIgnoreCase(trade.getRequest().getSymbol())).findFirst().ifPresent(k -> {
            if (k.getRatio() >= trade.getRequest().getExitKimp()) {
                log.info(">>> [EXIT] exit condition met");
                triggerMarketExit(botKey, trade);
            }
        });
    }

    private void triggerMarketExit(String botKey, ActiveTrade trade) {
        try {
            String symbol = trade.getRequest().getSymbol();
            String ex = trade.getRequest().getDomesticExchange().toUpperCase();
            Map<String, Object> domesticExitRes = "BITHUMB".equals(ex)
                    ? exchangeApiService.orderBithumb(trade.getUserId(), symbol, "ask", trade.getFilledQty(), null, "market")
                    : exchangeApiService.orderUpbit(trade.getUserId(), symbol, "ask", trade.getFilledQty(), null, "market");
            tradeOrderService.recordOrder(
                    trade.getUserId(), botKey, ex, "SPOT", "EXIT_DOMESTIC",
                    symbol, "SELL", null, "MARKET", trade.getFilledQty(), null, domesticExitRes
            );

            Map<String, Object> foreignExitRes = exchangeApiService.orderBinanceFutures(
                    trade.getUserId(), symbol, "BUY", "SHORT", trade.getHedgedQty(), null, "MARKET");
            tradeOrderService.recordOrder(
                    trade.getUserId(), botKey, "BINANCE", "FUTURES", "EXIT_FOREIGN",
                    symbol, "BUY", "SHORT", "MARKET", trade.getHedgedQty(), null, foreignExitRes
            );
            botStatusSyncService.sync(trade.getUserId(), trade.getRequest(), botKey, BotStatus.STOPPED);
            activeBots.remove(botKey);
        } catch (Exception e) {
            log.error("Exit Error: {}", e.getMessage());
            botStatusSyncService.sync(trade.getUserId(), trade.getRequest(), botKey, BotStatus.ERROR);
        }
    }

    private void checkSlTpCondition(String botKey, ActiveTrade trade, double currentPrice) {
        boolean trigger = (trade.getSlPrice() != null && currentPrice >= trade.getSlPrice()) ||
                (trade.getTpPrice() != null && currentPrice <= trade.getTpPrice());
        if (trigger) triggerMarketExit(botKey, trade);
    }

    private void checkForeignPositionAlive(String botKey, ActiveTrade trade) {
        double pos = exchangeApiService.getBinanceFuturesPosition(trade.getUserId(), trade.getRequest().getSymbol());
        if (pos == 0.0) {
            log.warn(">>> [SYSTEM] foreign position closed. close domestic leg");
            String ex = trade.getRequest().getDomesticExchange().toUpperCase();
            Map<String, Object> domesticCloseRes = "BITHUMB".equals(ex)
                    ? exchangeApiService.orderBithumb(trade.getUserId(), trade.getRequest().getSymbol(), "ask", trade.getFilledQty(), null, "market")
                    : exchangeApiService.orderUpbit(trade.getUserId(), trade.getRequest().getSymbol(), "ask", trade.getFilledQty(), null, "market");
            tradeOrderService.recordOrder(
                    trade.getUserId(), botKey, ex, "SPOT", "FAILSAFE_DOMESTIC",
                    trade.getRequest().getSymbol(), "SELL", null, "MARKET", trade.getFilledQty(), null, domesticCloseRes
            );
            botStatusSyncService.sync(trade.getUserId(), trade.getRequest(), botKey, BotStatus.ERROR);
            activeBots.remove(botKey);
        }
    }

    private String stopArbitrage(String botKey) {
        activeBots.remove(botKey);
        return botKey + " stopped";
    }

    private String extractDomesticOrderId(Map<String, Object> response) {
        Object uuid = response.get("uuid");
        if (uuid != null) {
            return String.valueOf(uuid);
        }
        Object orderId = response.get("order_id");
        if (orderId != null) {
            return String.valueOf(orderId);
        }
        Object id = response.get("id");
        return id != null ? String.valueOf(id) : null;
    }

    public Map<String, Boolean> getBotStatus() {
        Map<String, Boolean> status = new ConcurrentHashMap<>();
        activeBots.forEach((k, v) -> status.put(k, true));
        return status;
    }
}
