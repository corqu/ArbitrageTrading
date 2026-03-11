package corque.gimpalarm.coin.controller;

import corque.gimpalarm.coin.dto.TradingRequest;
import corque.gimpalarm.coin.service.TradingBotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/trading")
@RequiredArgsConstructor
public class TradingController {

    private final TradingBotService tradingBotService;

    /**
     * 매매 실행 (START / STOP)
     */
    @PostMapping("/execute")
    public ResponseEntity<String> executeTrade(@RequestBody TradingRequest request) {
        String result = tradingBotService.executeTrade(request);
        return ResponseEntity.ok(result);
    }

    /**
     * 현재 코인별 봇 상태 조회
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> getBotStatus() {
        return ResponseEntity.ok(tradingBotService.getBotStatus());
    }
}
