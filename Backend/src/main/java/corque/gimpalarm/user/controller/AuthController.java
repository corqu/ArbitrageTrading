package corque.gimpalarm.user.controller;

import corque.gimpalarm.common.util.JwtTokenProvider;
import corque.gimpalarm.user.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest request) {
        authService.signup(request.getEmail(), request.getPassword(), request.getNickname());
        return ResponseEntity.ok("Signup successful");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        String token = authService.login(request.getEmail(), request.getPassword());
        response.addCookie(jwtTokenProvider.createCookie(token));
        return ResponseEntity.ok("Login successful");
    }

    @PostMapping("/secrets")
    public ResponseEntity<?> saveSecrets(
            @AuthenticationPrincipal String email,
            @RequestBody SecretRequest request) {
        authService.saveSecrets(email, request.getExchange(), request.getApiKey(), request.getApiSecret());
        return ResponseEntity.ok("Secrets saved successfully");
    }

    @Data
    public static class SignupRequest {
        private String email;
        private String password;
        private String nickname;
    }

    @Data
    public static class LoginRequest {
        private String email;
        private String password;
    }

    @Data
    public static class SecretRequest {
        private String exchange;
        private String apiKey;
        private String apiSecret;
    }
}
