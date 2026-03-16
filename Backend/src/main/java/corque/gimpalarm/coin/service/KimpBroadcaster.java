package corque.gimpalarm.coin.service;

import corque.gimpalarm.coin.domain.KimchPremium;
import corque.gimpalarm.coin.dto.KimpResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class KimpBroadcaster {

    private final KimpService kimpService;
    private final CoinPriceService coinPriceService;
    private final SimpMessagingTemplate messagingTemplate;

    private int saveCounter = 0;

    /**
     * 0.5초마다 전체 김프를 계산하여 웹소켓으로 전송하고, 
     * InfluxDB에는 일정 주기마다 정제된 데이터를 저장합니다.
     */
    @Scheduled(fixedRate = 500)
    public void broadcastKimp() {
        Map<String, List<KimpResponseDto>> pairs = kimpService.calculateAllPairs();
        
        if (pairs.isEmpty()) return;

        // 1. 웹소켓 전송 (4개의 개별 엔드포인트)
        messagingTemplate.convertAndSend("/topic/kimp/ub-bn", pairs.get("ub-bn"));
        messagingTemplate.convertAndSend("/topic/kimp/ub-bb", pairs.get("ub-bb"));
        messagingTemplate.convertAndSend("/topic/kimp/bt-bn", pairs.get("bt-bn"));
        messagingTemplate.convertAndSend("/topic/kimp/bt-bb", pairs.get("bt-bb"));

        // 2. InfluxDB 저장 (평균화 로직 적용)
        saveAveragedKimpToInfluxDb(pairs);
    }

    private void saveAveragedKimpToInfluxDb(Map<String, List<KimpResponseDto>> pairs) {
        // InfluxDB는 5초(500ms * 10)에 한 번 저장
        if (++saveCounter < 10) return;
        saveCounter = 0;

        List<KimchPremium> kimpToSave = new ArrayList<>();

        // 바이낸스 기준 국내 평균 계산
        kimpToSave.addAll(calculateAverageForForeign(pairs.get("ub-bn"), pairs.get("bt-bn"), "BINANCE_FUTURES"));

        // 바이비트 기준 국내 평균 계산
        kimpToSave.addAll(calculateAverageForForeign(pairs.get("ub-bb"), pairs.get("bt-bb"), "BYBIT_FUTURES"));

        if (!kimpToSave.isEmpty()) {
            coinPriceService.saveKimchPremiums(kimpToSave);
        }
    }

    private List<KimchPremium> calculateAverageForForeign(List<KimpResponseDto> ubList, List<KimpResponseDto> btList, String foreignEx) {
        Map<String, KimpResponseDto> ubMap = (ubList != null) ? 
            ubList.stream().collect(Collectors.toMap(KimpResponseDto::getSymbol, d -> d, (v1, v2) -> v1)) : Collections.emptyMap();
        Map<String, KimpResponseDto> btMap = (btList != null) ? 
            btList.stream().collect(Collectors.toMap(KimpResponseDto::getSymbol, d -> d, (v1, v2) -> v1)) : Collections.emptyMap();

        Set<String> allSymbols = new HashSet<>(ubMap.keySet());
        allSymbols.addAll(btMap.keySet());

        return allSymbols.stream().map(symbol -> {
            KimpResponseDto ub = ubMap.get(symbol);
            KimpResponseDto bt = btMap.get(symbol);

            double avgRatio;
            Double fundingRate;
            Double tradeVolume;

            if (ub != null && bt != null) {
                // 양쪽 거래소에 있는 코인이면 두 개의 평균값을 저장
                avgRatio = (ub.getRatio() + bt.getRatio()) / 2.0;
                fundingRate = ub.getFundingRate(); // 펀딩비는 동일 해외 거래소 기준이므로 어느 것이나 무관
                tradeVolume = (ub.getTradeVolume() != null ? ub.getTradeVolume() : 0.0) 
                            + (bt.getTradeVolume() != null ? bt.getTradeVolume() : 0.0);
            } else if (ub != null) {
                // 업비트에만 있는 경우
                avgRatio = ub.getRatio();
                fundingRate = ub.getFundingRate();
                tradeVolume = ub.getTradeVolume();
            } else if (bt != null) {
                // 빗썸에만 있는 경우
                avgRatio = bt.getRatio();
                fundingRate = bt.getFundingRate();
                tradeVolume = bt.getTradeVolume();
            } else {
                return null;
            }

            return KimchPremium.builder()
                    .symbol(symbol)
                    .domesticExchange("AVERAGE") // 국내 거래소 평균값
                    .foreignExchange(foreignEx)
                    .ratio(avgRatio)
                    .fundingRate(fundingRate)
                    .tradeVolume(tradeVolume)
                    .build();
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    }
}
