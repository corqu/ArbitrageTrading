package corque.gimpalarm.tradeorder.service;

import corque.gimpalarm.tradeorder.domain.TradeOrder;
import corque.gimpalarm.tradeorder.dto.TradeOrderHistoryRowDto;
import corque.gimpalarm.tradeorder.repository.TradeOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TradeOrderHistoryService {

    private final TradeOrderRepository tradeOrderRepository;

    @Transactional(readOnly = true)
    public List<TradeOrderHistoryRowDto> getRecentOrders(Long userId) {
        List<TradeOrder> orders = tradeOrderRepository.findTop200ByUserIdOrderByIdDesc(userId);
        Map<String, List<TradeOrder>> grouped = orders.stream()
                .collect(Collectors.groupingBy(order -> order.getBotKey() + "|" + normalizeRole(order.getOrderRole())));

        List<HistoryRowWrapper> rows = new ArrayList<>();
        for (List<TradeOrder> group : grouped.values()) {
            List<TradeOrder> domesticOrders = group.stream()
                    .filter(this::isDomestic)
                    .sorted(Comparator.comparing(TradeOrder::getId).reversed())
                    .toList();
            List<TradeOrder> foreignOrders = group.stream()
                    .filter(this::isForeign)
                    .sorted(Comparator.comparing(TradeOrder::getId).reversed())
                    .toList();
            List<TradeOrder> singleOrders = group.stream()
                    .filter(order -> !isDomestic(order) && !isForeign(order))
                    .sorted(Comparator.comparing(TradeOrder::getId).reversed())
                    .toList();

            int pairCount = Math.max(domesticOrders.size(), foreignOrders.size());
            for (int i = 0; i < pairCount; i++) {
                TradeOrder domestic = i < domesticOrders.size() ? domesticOrders.get(i) : null;
                TradeOrder foreign = i < foreignOrders.size() ? foreignOrders.get(i) : null;
                rows.add(new HistoryRowWrapper(resolveSortId(domestic, foreign), toDto(domestic, foreign)));
            }

            for (TradeOrder order : singleOrders) {
                rows.add(new HistoryRowWrapper(order.getId(), toDto(order, null)));
            }
        }

        return rows.stream()
                .sorted(Comparator.comparing(HistoryRowWrapper::sortId).reversed())
                .map(HistoryRowWrapper::row)
                .toList();
    }

    private TradeOrderHistoryRowDto toDto(TradeOrder first, TradeOrder second) {
        String normalizedRole = normalizeRole(first != null ? first.getOrderRole() : second != null ? second.getOrderRole() : null);
        return TradeOrderHistoryRowDto.builder()
                .id(buildMergedId(first, second))
                .botKey(first != null ? first.getBotKey() : second != null ? second.getBotKey() : "-")
                .symbol(buildMergedValue(first != null ? first.getSymbol() : null, second != null ? second.getSymbol() : null))
                .phase(resolvePhase(normalizedRole))
                .exchange(buildMergedValue(first != null ? first.getExchange() : null, second != null ? second.getExchange() : null))
                .quantity(buildMergedValue(formatQuantity(first), formatQuantity(second)))
                .requestedQty(buildMergedValue(formatRequestedQty(first), formatRequestedQty(second)))
                .executedQty(buildMergedValue(formatExecutedQty(first), formatExecutedQty(second)))
                .remainingQty(buildMergedValue(formatRemainingQty(first), formatRemainingQty(second)))
                .requestedPrice(buildMergedValue(formatRequestedPrice(first), formatRequestedPrice(second)))
                .averagePrice(buildMergedValue(formatAveragePrice(first), formatAveragePrice(second)))
                .status(buildMergedValue(first != null ? first.getStatus() : null, second != null ? second.getStatus() : null))
                .build();
    }

    private String buildMergedId(TradeOrder first, TradeOrder second) {
        String left = first != null ? String.valueOf(first.getId()) : null;
        String right = second != null ? String.valueOf(second.getId()) : null;
        return buildMergedValue(left, right);
    }

    private String formatQuantity(TradeOrder order) {
        if (order == null) {
            return null;
        }
        Double quantity = order.getExecutedQty() != null && order.getExecutedQty() > 0
                ? order.getExecutedQty()
                : order.getRequestedQty();
        return quantity != null ? stripTrailingZeros(quantity) : null;
    }

    private String formatRequestedQty(TradeOrder order) {
        return formatNumeric(order != null ? order.getRequestedQty() : null);
    }

    private String formatExecutedQty(TradeOrder order) {
        return formatNumeric(order != null ? order.getExecutedQty() : null);
    }

    private String formatRemainingQty(TradeOrder order) {
        return formatNumeric(order != null ? order.getRemainingQty() : null);
    }

    private String formatRequestedPrice(TradeOrder order) {
        return formatNumeric(order != null ? order.getRequestedPrice() : null);
    }

    private String formatAveragePrice(TradeOrder order) {
        return formatNumeric(order != null ? order.getAveragePrice() : null);
    }

    private String formatNumeric(Double value) {
        return value != null ? stripTrailingZeros(value) : null;
    }

    private String stripTrailingZeros(Double value) {
        if (value == null) {
            return null;
        }
        String text = String.format(Locale.US, "%.8f", value);
        return text.indexOf('.') >= 0 ? text.replaceAll("0+$", "").replaceAll("\\.$", "") : text;
    }

    private String buildMergedValue(String first, String second) {
        if (first == null || first.isBlank()) {
            return second != null ? second : "-";
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        return first + " / " + second;
    }

    private boolean isDomestic(TradeOrder order) {
        return order.getOrderRole() != null && order.getOrderRole().endsWith("_DOMESTIC");
    }

    private boolean isForeign(TradeOrder order) {
        return order.getOrderRole() != null && order.getOrderRole().endsWith("_FOREIGN");
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "UNKNOWN";
        }
        if (role.endsWith("_DOMESTIC")) {
            return role.substring(0, role.length() - "_DOMESTIC".length());
        }
        if (role.endsWith("_FOREIGN")) {
            return role.substring(0, role.length() - "_FOREIGN".length());
        }
        return role;
    }

    private String resolvePhase(String normalizedRole) {
        return switch (normalizedRole) {
            case "ENTRY" -> "ENTRY";
            case "REBALANCE" -> "HOLD";
            case "EXIT", "STOP_LOSS", "TAKE_PROFIT", "FAILSAFE" -> "EXIT";
            default -> normalizedRole;
        };
    }

    private long resolveSortId(TradeOrder first, TradeOrder second) {
        long left = first != null ? first.getId() : Long.MIN_VALUE;
        long right = second != null ? second.getId() : Long.MIN_VALUE;
        return Math.max(left, right);
    }

    private record HistoryRowWrapper(long sortId, TradeOrderHistoryRowDto row) {
    }
}
