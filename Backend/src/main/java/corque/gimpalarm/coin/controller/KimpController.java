package corque.gimpalarm.coin.controller;

import corque.gimpalarm.coin.domain.KimchPremium;
import corque.gimpalarm.coin.dto.KimpResponseDto;
import corque.gimpalarm.coin.service.CoinPriceService;
import corque.gimpalarm.coin.service.KimpService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/kimp")
@RequiredArgsConstructor
public class KimpController {

    private final KimpService kimpService;
    private final CoinPriceService coinPriceService;

    @GetMapping("/current")
    public ResponseEntity<List<KimpResponseDto>> getCurrentKimpList() {
        List<KimpResponseDto> currentList = kimpService.calculateAllKimp();
        return ResponseEntity.ok(currentList);
    }

    @GetMapping("/current/pairs")
    public ResponseEntity<Map<String, List<KimpResponseDto>>> getCurrentKimpPairs() {
        return ResponseEntity.ok(kimpService.calculateAllPairs());
    }

    @GetMapping("/history")
    public ResponseEntity<List<KimpResponseDto>> getKimpHistory(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "-24h") String range,
            @RequestParam(defaultValue = "AVERAGE") String domesticExchange,
            @RequestParam(defaultValue = "BINANCE_FUTURES") String foreignExchange) {
        List<KimchPremium> history = coinPriceService.getKimpHistory(symbol, range, domesticExchange, foreignExchange);
        List<KimpResponseDto> dtoList = history.stream()
                .map(h -> KimpResponseDto.builder()
                        .symbol(h.getSymbol())
                        .domesticExchange(h.getDomesticExchange())
                        .foreignExchange(h.getForeignExchange())
                        .standardRatio(h.getStandardRatio())
                        .entryRatio(h.getEntryRatio())
                        .exitRatio(h.getExitRatio())
                        .fundingRate(h.getFundingRate())
                        .tradeVolume(h.getTradeVolume())
                        .time(h.getTime())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtoList);
    }
}
