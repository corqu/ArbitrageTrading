package corque.gimpalarm.user.controller;

import corque.gimpalarm.user.domain.User;
import corque.gimpalarm.user.domain.UserCredential;
import corque.gimpalarm.user.domain.UserPrincipal;
import corque.gimpalarm.user.dto.UserCredentialRequestDto;
import corque.gimpalarm.user.repository.UserRepository;
import corque.gimpalarm.user.service.UserCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user/credentials")
@RequiredArgsConstructor
public class UserCredentialController {

    private final UserCredentialService userCredentialService;
    private final corque.gimpalarm.user.service.ExchangeApiService exchangeApiService;
    private final corque.gimpalarm.coin.dto.PriceManager priceManager;

    @GetMapping("/list")
    public ResponseEntity<List<String>> list(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(userCredentialService.getUserCredentials(userPrincipal.getId()));
    }

    @DeleteMapping("/unbind/{exchange}")
    public ResponseEntity<Void> unbind(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String exchange) {
        if (userPrincipal == null) {
            return ResponseEntity.status(401).build();
        }
        userCredentialService.deleteCredential(userPrincipal.getId(), exchange);

        return ResponseEntity.ok().build();
    }

    /**
     * 특정 거래소의 연동된 자산 현황 조회
     */
    @GetMapping("/assets")
    public ResponseEntity<Map<String, Object>> getAssets(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam String exchange) {
        if (userPrincipal == null) return ResponseEntity.status(401).build();

        try {
            Map<String, Object> assets = exchangeApiService.getExchangeAssets(userPrincipal.getId(), exchange);

            Double usdKrw = priceManager.getCurrentUsdKrw();
            assets.put("usdKrw", (usdKrw == null || usdKrw == 0) ? 1450.0 : usdKrw);

            return ResponseEntity.ok(assets);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "자산 조회 중 에러가 발생했습니다: " + e.getMessage()));
        }
    }


    /**
     * 최근 매매 내역 조회
     */
    @GetMapping("/orders")
    public ResponseEntity<List<Map<String, Object>>> getOrders(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (userPrincipal == null) return ResponseEntity.status(401).build();

        // 매매 내역은 DB에서 조회 (추후 구현 예정)
        List<Map<String, Object>> orders = new ArrayList<>();
        return ResponseEntity.ok(orders);
    }
}
