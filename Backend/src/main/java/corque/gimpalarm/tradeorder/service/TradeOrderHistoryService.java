package corque.gimpalarm.tradeorder.service;

import corque.gimpalarm.tradeorder.domain.TradeOrder;
import corque.gimpalarm.tradeorder.dto.TradeOrderHistoryPageDto;
import corque.gimpalarm.tradeorder.dto.TradeOrderHistoryRowDto;
import corque.gimpalarm.tradeorder.repository.TradeOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TradeOrderHistoryService {

    private static final DateTimeFormatter HISTORY_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TradeOrderRepository tradeOrderRepository;

    @Transactional(readOnly = true)
    public TradeOrderHistoryPageDto getRecentOrders(Long userId, int page, int size) {
        List<TradeOrder> orders = tradeOrderRepository.findTop200ByUserIdOrderByIdDesc(userId);
        Map<String, List<TradeOrder>> grouped = orders.stream()
                .collect(Collectors.groupingBy(TradeOrder::getBotKey));

        List<HistoryRowWrapper> rows = new ArrayList<>();
        for (List<TradeOrder> group : grouped.values()) {
            List<TradeOrder> sorted = group.stream()
                    .sorted(Comparator.comparing(TradeOrder::getId))
                    .toList();

            List<List<TradeOrder>> sessions = splitSessions(sorted);
            for (List<TradeOrder> session : sessions) {
                if (!session.isEmpty()) {
                    rows.add(new HistoryRowWrapper(resolveSortId(session), toDto(session)));
                }
            }
        }

        List<TradeOrderHistoryRowDto> allRows = rows.stream()
                .sorted(Comparator.comparing(HistoryRowWrapper::sortId).reversed())
                .map(HistoryRowWrapper::row)
                .toList();

        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        int fromIndex = Math.min(safePage * safeSize, allRows.size());
        int toIndex = Math.min(fromIndex + safeSize, allRows.size());
        int totalPages = (int) Math.ceil((double) allRows.size() / safeSize);

        return TradeOrderHistoryPageDto.builder()
                .content(allRows.subList(fromIndex, toIndex))
                .page(safePage)
                .size(safeSize)
                .totalElements(allRows.size())
                .totalPages(totalPages)
                .hasNext(safePage + 1 < totalPages)
                .hasPrevious(safePage > 0)
                .build();
    }

    private List<List<TradeOrder>> splitSessions(List<TradeOrder> orders) {
        List<List<TradeOrder>> sessions = new ArrayList<>();
        List<TradeOrder> current = new ArrayList<>();

        for (TradeOrder order : orders) {
            if (!current.isEmpty() && isEntry(order) && shouldStartNewSession(current)) {
                sessions.add(current);
                current = new ArrayList<>();
            }
            current.add(order);
        }

        if (!current.isEmpty()) {
            sessions.add(current);
        }

        return sessions;
    }

    private boolean shouldStartNewSession(List<TradeOrder> current) {
        return hasTerminalExit(current) || isCanceledEntrySession(current);
    }

    private TradeOrderHistoryRowDto toDto(List<TradeOrder> session) {
        TradeOrder representative = session.get(0);
        String phase = resolveSessionPhase(session);
        String status = resolveSessionStatus(session, phase);
        String quantity = resolveSessionQuantity(session);
        String averagePrice = resolveSessionAveragePrice(session);
        String requestedPrice = resolveSessionRequestedPrice(session);

        return TradeOrderHistoryRowDto.builder()
                .id(representative.getBotKey() + ":" + resolveSortId(session))
                .botKey(representative.getBotKey())
                .symbol(representative.getSymbol())
                .entryAt(formatTime(resolveEntryTime(session)))
                .exitAt(formatTime(resolveExitTime(session)))
                .phase(phase)
                .exchange(resolveExchanges(session))
                .quantity(quantity)
                .requestedQty(quantity)
                .executedQty(quantity)
                .remainingQty(resolveSessionRemainingQty(session))
                .requestedPrice(requestedPrice)
                .averagePrice(averagePrice)
                .status(status)
                .build();
    }

    private LocalDateTime resolveEntryTime(List<TradeOrder> session) {
        return session.stream()
                .filter(this::isEntry)
                .map(TradeOrder::getCreatedAt)
                .filter(time -> time != null)
                .findFirst()
                .orElseGet(() -> session.stream()
                        .map(TradeOrder::getCreatedAt)
                        .filter(time -> time != null)
                        .findFirst()
                        .orElse(null));
    }

    private LocalDateTime resolveExitTime(List<TradeOrder> session) {
        return session.stream()
                .filter(this::isTerminalRole)
                .map(TradeOrder::getCreatedAt)
                .filter(time -> time != null)
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private String formatTime(LocalDateTime time) {
        return time != null ? time.format(HISTORY_TIME_FORMATTER) : "-";
    }

    private String resolveExchanges(List<TradeOrder> session) {
        return session.stream()
                .map(TradeOrder::getExchange)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), exchanges ->
                        exchanges.isEmpty() ? "-" : String.join(" / ", exchanges)));
    }

    private String resolveSessionQuantity(List<TradeOrder> session) {
        Double terminalQty = session.stream()
                .filter(this::isTerminalRole)
                .map(this::resolvePreferredQty)
                .filter(value -> value != null && value > 0)
                .findFirst()
                .orElse(null);
        if (terminalQty != null) {
            return stripTrailingZeros(terminalQty);
        }

        Double entryQty = session.stream()
                .filter(this::isEntry)
                .map(this::resolvePreferredQty)
                .filter(value -> value != null && value > 0)
                .findFirst()
                .orElse(null);
        return entryQty != null ? stripTrailingZeros(entryQty) : "-";
    }

    private String resolveSessionRequestedPrice(List<TradeOrder> session) {
        Double price = session.stream()
                .filter(this::isEntry)
                .map(TradeOrder::getRequestedPrice)
                .filter(value -> value != null && value > 0)
                .findFirst()
                .orElse(null);
        return price != null ? stripTrailingZeros(price) : "-";
    }

    private String resolveSessionAveragePrice(List<TradeOrder> session) {
        Double price = session.stream()
                .filter(this::isEntry)
                .map(TradeOrder::getAveragePrice)
                .filter(value -> value != null && value > 0)
                .findFirst()
                .orElse(null);
        return price != null ? stripTrailingZeros(price) : "-";
    }

    private String resolveSessionRemainingQty(List<TradeOrder> session) {
        Double remaining = session.stream()
                .filter(this::isEntry)
                .map(TradeOrder::getRemainingQty)
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
        return remaining != null ? stripTrailingZeros(remaining) : "-";
    }

    private String resolveSessionPhase(List<TradeOrder> session) {
        if (hasTerminalExit(session)) {
            return "EXIT";
        }
        if (isCanceledEntrySession(session)) {
            return "CANCEL";
        }
        if (hasHoldingEvidence(session)) {
            return "HOLD";
        }
        return "ENTRY";
    }

    private String resolveSessionStatus(List<TradeOrder> session, String phase) {
        if ("CANCEL".equals(phase)) {
            return "CANCELED";
        }

        for (int i = session.size() - 1; i >= 0; i--) {
            String status = session.get(i).getStatus();
            if (status != null && !status.isBlank()) {
                return status.toUpperCase(Locale.US);
            }
        }
        return "-";
    }

    private boolean hasHoldingEvidence(List<TradeOrder> session) {
        return session.stream().anyMatch(order ->
                isHoldingRole(order)
                        || (isEntry(order) && order.getExecutedQty() != null && order.getExecutedQty() > 0)
                        || isFilledStatus(order.getStatus()));
    }

    private boolean hasTerminalExit(List<TradeOrder> session) {
        return session.stream().anyMatch(this::isTerminalRole);
    }

    private boolean isCanceledEntrySession(List<TradeOrder> session) {
        List<TradeOrder> entryOrders = session.stream()
                .filter(this::isEntry)
                .toList();

        if (entryOrders.isEmpty() || hasTerminalExit(session) || hasHoldingEvidence(session) && session.stream().anyMatch(order -> !isEntry(order))) {
            return false;
        }

        return entryOrders.stream().allMatch(order ->
                isCanceledStatus(order.getStatus())
                        || (order.getExecutedQty() == null || order.getExecutedQty() == 0)
                        && (order.getRemainingQty() == null || order.getRemainingQty() > 0));
    }

    private boolean isEntry(TradeOrder order) {
        return order.getOrderRole() != null && order.getOrderRole().startsWith("ENTRY_");
    }

    private boolean isHoldingRole(TradeOrder order) {
        String role = order.getOrderRole();
        return role != null && (
                role.startsWith("REBALANCE_")
                        || "STOP_LOSS".equals(role)
                        || "TAKE_PROFIT".equals(role)
        );
    }

    private boolean isTerminalRole(TradeOrder order) {
        String role = order.getOrderRole();
        return role != null && (
                role.startsWith("EXIT_")
                        || role.startsWith("FAILSAFE_")
        );
    }

    private boolean isCanceledStatus(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.toUpperCase(Locale.US);
        return normalized.contains("CANCEL")
                || normalized.contains("EXPIRED")
                || normalized.contains("REJECT");
    }

    private boolean isFilledStatus(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.toUpperCase(Locale.US);
        return "FILLED".equals(normalized) || "DONE".equals(normalized);
    }

    private Double resolvePreferredQty(TradeOrder order) {
        if (order.getExecutedQty() != null && order.getExecutedQty() > 0) {
            return order.getExecutedQty();
        }
        return order.getRequestedQty();
    }

    private String stripTrailingZeros(Double value) {
        if (value == null) {
            return null;
        }
        String text = String.format(Locale.US, "%.8f", value);
        return text.indexOf('.') >= 0 ? text.replaceAll("0+$", "").replaceAll("\\.$", "") : text;
    }

    private long resolveSortId(List<TradeOrder> session) {
        return session.stream()
                .map(TradeOrder::getId)
                .max(Long::compareTo)
                .orElse(Long.MIN_VALUE);
    }

    private record HistoryRowWrapper(long sortId, TradeOrderHistoryRowDto row) {
    }
}
