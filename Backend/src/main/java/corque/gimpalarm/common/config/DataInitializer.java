package corque.gimpalarm.common.config;

import corque.gimpalarm.coin.service.CoinBatchService;
import corque.gimpalarm.coin.service.CoinPriceService;
import corque.gimpalarm.coin.service.HistoricalDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final CoinBatchService coinBatchService;
    private final HistoricalDataService historicalDataService;
    private final CoinPriceService coinPriceService;

    @Override
    public void run(String... args) throws Exception {
        log.info("애플리케이션 초기화 데이터 체크 시작...");

        // 1. 코인 목록 최신화 (DB에 코인이 있어야 과거 데이터 수집 가능)
        log.info("기본 코인 목록 확인 및 갱신 중...");
        coinBatchService.updateSupportedCoins();

        // 2. 과거 데이터(6개월치) 존재 여부 확인 후 수집
        if (!coinPriceService.hasHistoricalData()) {
            log.info("InfluxDB에 과거 데이터가 없습니다. 6개월치 데이터 수집을 시작합니다 (비동기).");
            historicalDataService.importSixMonthsHistory();
        } else {
            log.info("이미 과거 데이터가 존재합니다. 추가 수집을 건너뜁니다.");
        }
        
        log.info("초기화 체크 완료.");
    }
}
