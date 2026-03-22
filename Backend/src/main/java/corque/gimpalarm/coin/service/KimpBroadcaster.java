package corque.gimpalarm.coin.service;

import corque.gimpalarm.coin.domain.KimchPremium;
import corque.gimpalarm.coin.dto.KimpResponseDto;
import corque.gimpalarm.coin.dto.PriceChangedEvent;
import corque.gimpalarm.coin.dto.RealtimeKimpDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class KimpBroadcaster {

    private static final List<String> PAIR_KEYS = List.of("ub-bn", "ub-bb", "bt-bn", "bt-bb");

    private final KimpService kimpService;
    private final CoinPriceService coinPriceService;
    private final SimpMessagingTemplate messagingTemplate;

    private final Map<String, Map<String, KimpResponseDto>> pairSnapshots = new ConcurrentHashMap<>();

    @PostConstruct
    void initializeSnapshots() {
        PAIR_KEYS.forEach(pairKey -> pairSnapshots.put(pairKey, new ConcurrentHashMap<>()));
        refreshAllSnapshots();
    }

    @EventListener
    public void onPriceChanged(PriceChangedEvent event) {
        if (isUsdKrwEvent(event.getKey())) {
            broadcastAllSymbolUpdates();
            return;
        }

        String symbol = extractSymbol(event.getKey());
        if (symbol == null || symbol.isBlank()) {
            return;
        }

        Map<String, KimpResponseDto> updates = kimpService.calculatePairsForSymbol(symbol);
        for (String pairKey : PAIR_KEYS) {
            Map<String, KimpResponseDto> snapshot = pairSnapshots.get(pairKey);
            KimpResponseDto updated = updates.get(pairKey);

            if (updated == null) {
                snapshot.remove(symbol);
                continue;
            }

            snapshot.put(symbol, updated);
            publishPairUpdate(pairKey, updated);
        }
    }

    @Scheduled(fixedRate = 5000)
    public void saveAveragedKimpToInfluxDb() {
        Map<String, List<KimpResponseDto>> pairs = getSnapshotLists();
        List<KimchPremium> kimpToSave = new ArrayList<>();

        kimpToSave.addAll(calculateAverageForForeign(pairs.get("ub-bn"), pairs.get("bt-bn"), "BINANCE_FUTURES"));
        kimpToSave.addAll(calculateAverageForForeign(pairs.get("ub-bb"), pairs.get("bt-bb"), "BYBIT_FUTURES"));

        if (!kimpToSave.isEmpty()) {
            coinPriceService.saveKimchPremiums(kimpToSave);
        }
    }

    private void refreshAllSnapshots() {
        Map<String, List<KimpResponseDto>> pairs = kimpService.calculateAllPairs();
        for (String pairKey : PAIR_KEYS) {
            Map<String, KimpResponseDto> snapshot = pairSnapshots.get(pairKey);
            snapshot.clear();
            for (KimpResponseDto dto : pairs.getOrDefault(pairKey, Collections.emptyList())) {
                snapshot.put(dto.getSymbol(), dto);
            }
        }
    }

    private void broadcastAllSymbolUpdates() {
        refreshAllSnapshots();

        for (String pairKey : PAIR_KEYS) {
            List<KimpResponseDto> snapshot = new ArrayList<>(pairSnapshots.get(pairKey).values());
            snapshot.sort(Comparator.comparing(KimpResponseDto::getSymbol));
            for (KimpResponseDto dto : snapshot) {
                publishPairUpdate(pairKey, dto);
            }
        }
    }

    private void publishPairUpdate(String pairKey, KimpResponseDto dto) {
        RealtimeKimpDto realtimeDto = RealtimeKimpDto.builder()
                .symbol(dto.getSymbol())
                .standardRatio(dto.getStandardRatio())
                .entryRatio(dto.getEntryRatio())
                .exitRatio(dto.getExitRatio())
                .build();
        messagingTemplate.convertAndSend("/topic/kimp/" + pairKey, List.of(realtimeDto));
        messagingTemplate.convertAndSend(buildSymbolTopic(pairKey, dto.getSymbol()), realtimeDto);
    }

    private String buildSymbolTopic(String pairKey, String symbol) {
        return "/topic/kimp/" + pairKey + "/" + symbol.toUpperCase();
    }

    private Map<String, List<KimpResponseDto>> getSnapshotLists() {
        Map<String, List<KimpResponseDto>> pairs = new HashMap<>();
        for (String pairKey : PAIR_KEYS) {
            List<KimpResponseDto> snapshot = new ArrayList<>(pairSnapshots.get(pairKey).values());
            snapshot.sort(Comparator.comparing(KimpResponseDto::getSymbol));
            pairs.put(pairKey, snapshot);
        }
        return pairs;
    }

    private String extractSymbol(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }

        int separatorIndex = key.lastIndexOf('_');
        if (separatorIndex >= 0 && separatorIndex < key.length() - 1) {
            return key.substring(separatorIndex + 1).toUpperCase();
        }

        return key.toUpperCase();
    }

    private boolean isUsdKrwEvent(String key) {
        return "KRW-USDT".equalsIgnoreCase(key);
    }

    private List<KimchPremium> calculateAverageForForeign(List<KimpResponseDto> ubList, List<KimpResponseDto> btList, String foreignEx) {
        Map<String, KimpResponseDto> ubMap = (ubList != null)
                ? ubList.stream().collect(Collectors.toMap(KimpResponseDto::getSymbol, dto -> dto, (left, right) -> left))
                : Collections.emptyMap();
        Map<String, KimpResponseDto> btMap = (btList != null)
                ? btList.stream().collect(Collectors.toMap(KimpResponseDto::getSymbol, dto -> dto, (left, right) -> left))
                : Collections.emptyMap();

        Set<String> allSymbols = new HashSet<>(ubMap.keySet());
        allSymbols.addAll(btMap.keySet());

        return allSymbols.stream()
                .map(symbol -> buildAverageKimp(symbol, ubMap.get(symbol), btMap.get(symbol), foreignEx))
                .filter(Objects::nonNull)
                .toList();
    }

    private Double averageRatio(Double first, Double second, double fallback) {
        if (first != null && second != null) {
            return (first + second) / 2.0;
        }
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return fallback;
    }

    private KimchPremium buildAverageKimp(String symbol, KimpResponseDto ub, KimpResponseDto bt, String foreignEx) {
        Double avgStandardRatio;
        Double avgEntryRatio;
        Double avgExitRatio;
        Double fundingRate;
        Double tradeVolume;

        if (ub != null && bt != null) {
            avgStandardRatio = averageRatio(ub.getStandardRatio(), bt.getStandardRatio(), 0.0);
            avgEntryRatio = averageRatio(ub.getEntryRatio(), bt.getEntryRatio(), avgStandardRatio);
            avgExitRatio = averageRatio(ub.getExitRatio(), bt.getExitRatio(), avgStandardRatio);
            fundingRate = ub.getFundingRate();
            tradeVolume = (ub.getTradeVolume() != null ? ub.getTradeVolume() : 0.0)
                    + (bt.getTradeVolume() != null ? bt.getTradeVolume() : 0.0);
        } else if (ub != null) {
            avgStandardRatio = ub.getStandardRatio();
            avgEntryRatio = averageRatio(ub.getEntryRatio(), null, avgStandardRatio != null ? avgStandardRatio : 0.0);
            avgExitRatio = averageRatio(ub.getExitRatio(), null, avgStandardRatio != null ? avgStandardRatio : 0.0);
            fundingRate = ub.getFundingRate();
            tradeVolume = ub.getTradeVolume();
        } else if (bt != null) {
            avgStandardRatio = bt.getStandardRatio();
            avgEntryRatio = averageRatio(bt.getEntryRatio(), null, avgStandardRatio != null ? avgStandardRatio : 0.0);
            avgExitRatio = averageRatio(bt.getExitRatio(), null, avgStandardRatio != null ? avgStandardRatio : 0.0);
            fundingRate = bt.getFundingRate();
            tradeVolume = bt.getTradeVolume();
        } else {
            return null;
        }

        if (avgStandardRatio == null) {
            return null;
        }

        return KimchPremium.builder()
                .symbol(symbol)
                .domesticExchange("AVERAGE")
                .foreignExchange(foreignEx)
                .standardRatio(avgStandardRatio)
                .entryRatio(avgEntryRatio)
                .exitRatio(avgExitRatio)
                .fundingRate(fundingRate)
                .tradeVolume(tradeVolume)
                .build();
    }
}
