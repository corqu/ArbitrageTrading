package corque.gimpalarm.user.controller;

import corque.gimpalarm.coin.dto.PriceManager;
import corque.gimpalarm.tradeorder.dto.TradeOrderHistoryPageDto;
import corque.gimpalarm.tradeorder.dto.TradeOrderHistoryRowDto;
import corque.gimpalarm.tradeorder.service.TradeOrderHistoryService;
import corque.gimpalarm.user.domain.UserPrincipal;
import corque.gimpalarm.user.dto.UserCredentialRequestDto;
import corque.gimpalarm.user.service.ExchangeApiService;
import corque.gimpalarm.user.service.UserCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user/credentials")
@RequiredArgsConstructor
public class UserCredentialController {

    private final UserCredentialService userCredentialService;
    private final ExchangeApiService exchangeApiService;
    private final PriceManager priceManager;
    private final TradeOrderHistoryService tradeOrderHistoryService;

    @GetMapping("/list")
    public ResponseEntity<List<String>> list(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(userCredentialService.getUserCredentials(userPrincipal.getId()));
    }

    @PostMapping("/bind")
    public ResponseEntity<Long> bind(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody UserCredentialRequestDto requestDto) {
        if (userPrincipal == null) {
            return ResponseEntity.status(401).build();
        }

        Long credentialId = userCredentialService.registerCredential(userPrincipal.getId(), requestDto);
        return ResponseEntity.ok(credentialId);
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

    @GetMapping("/assets")
    public ResponseEntity<Map<String, Object>> getAssets(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam String exchange) {
        if (userPrincipal == null) {
            return ResponseEntity.status(401).build();
        }

        try {
            Map<String, Object> assets = exchangeApiService.getExchangeAssets(userPrincipal.getId(), exchange);
            Double usdKrw = priceManager.getCurrentUsdKrw();
            assets.put("usdKrw", (usdKrw == null || usdKrw == 0) ? 1450.0 : usdKrw);
            return ResponseEntity.ok(assets);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to fetch assets: " + e.getMessage()));
        }
    }

    @GetMapping("/orders")
    public ResponseEntity<TradeOrderHistoryPageDto> getOrders(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (userPrincipal == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(tradeOrderHistoryService.getRecentOrders(userPrincipal.getId(), page, size));
    }
}
