package corque.gimpalarm.user.controller;

import corque.gimpalarm.user.dto.UserCredentialRequestDto;
import corque.gimpalarm.user.service.UserCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/credentials")
@RequiredArgsConstructor
public class UserCredentialController {

    private final UserCredentialService userCredentialService;

    @PostMapping("/register")
    public ResponseEntity<Long> register(@RequestBody UserCredentialRequestDto requestDto) {
        Long credentialId = userCredentialService.registerCredential(requestDto);
        return ResponseEntity.ok(credentialId);
    }
}
