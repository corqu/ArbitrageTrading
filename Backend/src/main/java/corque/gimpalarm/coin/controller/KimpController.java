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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/kimp")
@RequiredArgsConstructor
public class KimpController {

    private final KimpService kimpService;
    private final CoinPriceService coinPriceService;

    /**
     * 현재 수익률 순으로 정렬된 김프/차익거래 리스트를 반환합니다.
     */
    @GetMapping("/current")
    public ResponseEntity<List<KimpResponseDto>> getCurrentKimpList() {
        List<KimpResponseDto> currentList = kimpService.calculateAllKimp();
        return ResponseEntity.ok(currentList);
    }

    /**
     * 특정 코인의 과거 김프/펀딩피 히스토리를 반환합니다.
     */
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
                        .ratio(h.getRatio())
                        .fundingRate(h.getFundingRate())
                        .adjustedApr(h.getAdjustedApr())
                        .liquidationPrice(h.getLiquidationPrice())
                        .tradeVolume(h.getTradeVolume())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtoList);
    }
}
