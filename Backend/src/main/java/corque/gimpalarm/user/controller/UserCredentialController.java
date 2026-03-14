package corque.gimpalarm.user.controller;

import corque.gimpalarm.user.domain.User;
import corque.gimpalarm.user.domain.UserPrincipal;
import corque.gimpalarm.user.dto.UserCredentialRequestDto;
import corque.gimpalarm.user.repository.UserRepository;
import corque.gimpalarm.user.service.UserCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/credentials")
@RequiredArgsConstructor
public class UserCredentialController {

    private final UserCredentialService userCredentialService;
    private final UserRepository userRepository;

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

        User user = userRepository.findByEmail(userPrincipal.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userPrincipal.getEmail()));

        userCredentialService.deleteCredential(user, requestDto.getExchange());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{exchange}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String exchange) {
        if (userPrincipal == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findByEmail(userPrincipal.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userPrincipal.getEmail()));

        userCredentialService.deleteCredential(user, exchange);
        return ResponseEntity.noContent().build();
    }
}
