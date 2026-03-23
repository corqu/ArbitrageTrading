package corque.gimpalarm.coin.controller;

import corque.gimpalarm.coin.dto.arbitrage.BacktestResponse;
import corque.gimpalarm.coin.service.ArbitrageBacktestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/arbitrage")
@RequiredArgsConstructor
public class ArbitrageController {

    private final ArbitrageBacktestService backtestService;

    /**
     * 과거 데이터를 기반으로 김프 매매 시뮬레이션을 실행합니다.
     */
    @GetMapping("/backtest")
    public ResponseEntity<BacktestResponse> runBacktest(
            @RequestParam String symbol,
            @RequestParam double entryKimp,
            @RequestParam double exitKimp,
            @RequestParam(defaultValue = "-30d") String range) {
        
        BacktestResponse result = backtestService.runBacktest(symbol, entryKimp, exitKimp, range);
        return ResponseEntity.ok(result);
    }
}
