package corque.gimpalarm.coin.controller;

import corque.gimpalarm.coin.domain.KimchPremium;
import corque.gimpalarm.coin.service.CoinPriceService;
import corque.gimpalarm.coin.service.KimpService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    public ResponseEntity<List<KimchPremium>> getCurrentKimpList() {
        List<KimchPremium> currentList = kimpService.getCurrentKimpList();
        return ResponseEntity.ok(currentList);
    }

    /**
     * 특정 코인의 과거 김프/펀딩피 히스토리를 반환합니다.
     * range 예시: -1h, -24h, -7d
     */
    @GetMapping("/history")
    public ResponseEntity<List<KimchPremium>> getKimpHistory(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "-24h") String range) {
        List<KimchPremium> history = coinPriceService.getKimpHistory(symbol, range);
        return ResponseEntity.ok(history);
    }
}
