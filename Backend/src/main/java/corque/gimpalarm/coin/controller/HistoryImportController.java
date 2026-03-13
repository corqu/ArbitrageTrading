package corque.gimpalarm.coin.controller;

import corque.gimpalarm.coin.service.HistoricalDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryImportController {

    private final HistoricalDataService historicalDataService;

    /**
     * 6개월치 과거 데이터를 수집하기 시작합니다. (비동기)
     */
    @PostMapping("/import")
    public ResponseEntity<Map<String, String>> startImport() {
        historicalDataService.importSixMonthsHistory();
        return ResponseEntity.ok(Map.of("message", "과거 데이터 수집이 백그라운드에서 시작되었습니다. 로그를 확인하세요."));
    }
}
