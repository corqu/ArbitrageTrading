package corque.gimpalarm.userbot.controller;

import corque.gimpalarm.coin.dto.TradingRequest;
import corque.gimpalarm.userbot.dto.UserBotResponseDto;
import corque.gimpalarm.userbot.service.UserBotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user-bots")
@RequiredArgsConstructor
public class UserBotController {

    private final UserBotService userBotService;

    @GetMapping
    public ResponseEntity<List<UserBotResponseDto>> getMyBots(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(userBotService.getMyBots(userDetails.getUsername()));
    }

    @PostMapping
    public ResponseEntity<UserBotResponseDto> subscribeBot(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody TradingRequest request) {
        return ResponseEntity.ok(userBotService.subscribeBot(userDetails.getUsername(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserBotResponseDto> updateBot(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestBody TradingRequest request) {
        return ResponseEntity.ok(userBotService.updateBot(userDetails.getUsername(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBot(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        userBotService.deleteBot(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }
}
