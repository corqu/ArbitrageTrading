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

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user/credentials")
@RequiredArgsConstructor
public class UserCredentialController {

    private final UserCredentialService userCredentialService;

    @GetMapping("/list")
    public ResponseEntity<List<String>> list(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(userCredentialService.getUserCredentials(userPrincipal.getId()));
    }

    @PostMapping("/register")
    public ResponseEntity<Long> register(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody UserCredentialRequestDto requestDto) {
        if (userPrincipal == null) {
            return ResponseEntity.status(401).build();
        }
        
        requestDto.setUserId(userPrincipal.getId());
        Long credentialId = userCredentialService.registerCredential(requestDto);
        return ResponseEntity.ok(credentialId);
    }

    @DeleteMapping("/unregister")
    public ResponseEntity<Void> unregister(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody UserCredentialRequestDto requestDto) {
        if (userPrincipal == null) {
            return ResponseEntity.status(401).build();
        }

        userCredentialService.deleteCredential(userPrincipal.getId(), requestDto.getExchange());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{exchange}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String exchange) {
        if (userPrincipal == null) {
            return ResponseEntity.status(401).build();
        }

        userCredentialService.deleteCredential(userPrincipal.getId(), exchange);
        return ResponseEntity.noContent().build();
    }
}
