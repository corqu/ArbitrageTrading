package corque.gimpalarm.user.controller;

import corque.gimpalarm.common.util.JwtTokenProvider;
import corque.gimpalarm.user.domain.UserPrincipal;
import corque.gimpalarm.user.service.AuthService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
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
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        AuthService.TokenResponse tokens = authService.login(request.getEmail(), request.getPassword());
        
        ResponseCookie accessCookie = jwtTokenProvider.createAccessTokenCookie(tokens.getAccessToken());
        ResponseCookie refreshCookie = jwtTokenProvider.createRefreshTokenCookie(tokens.getRefreshToken());
        
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body("Login successful");
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (userPrincipal != null) {
            authService.logout(userPrincipal.getEmail());
        }
        
        ResponseCookie deleteAccess = jwtTokenProvider.createLogoutCookie("accessToken");
        ResponseCookie deleteRefresh = jwtTokenProvider.createLogoutCookie("refreshToken");
        
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteAccess.toString())
                .header(HttpHeaders.SET_COOKIE, deleteRefresh.toString())
                .body("Logout successful");
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(jakarta.servlet.http.HttpServletRequest request) {
        String refreshToken = jwtTokenProvider.resolveRefreshToken(request);
        if (refreshToken == null) {
            return ResponseEntity.status(401).body("Refresh token not found");
        }

        try {
            String newAccessToken = authService.refreshAccessToken(refreshToken);
            ResponseCookie newAccessCookie = jwtTokenProvider.createAccessTokenCookie(newAccessToken);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, newAccessCookie.toString())
                    .body("Token refreshed");
        } catch (Exception e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal UserPrincipal userPrincipal, jakarta.servlet.http.HttpServletRequest request) {
        if (userPrincipal == null) {
            return ResponseEntity.status(401).body("Not authenticated");
        }
        
        String token = jwtTokenProvider.resolveAccessToken(request);
        long expiresIn = (token != null) ? jwtTokenProvider.getRemainingTimeSeconds(token) : 0;
        
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("nickname", userPrincipal.getNickname());
        response.put("expiresIn", expiresIn);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/check-nickname")
    public ResponseEntity<?> checkNickname(@RequestParam String nickname) {
        boolean isAvailable = authService.isNicknameAvailable(nickname);
        return ResponseEntity.ok(java.util.Collections.singletonMap("available", isAvailable));
    }

    @PostMapping("/secrets")
    public ResponseEntity<?> saveSecrets(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody SecretRequest request) {
        if (userPrincipal == null) {
            return ResponseEntity.status(401).body("Not authenticated");
        }
        authService.saveSecrets(userPrincipal.getEmail(), request.getExchange(), request.getApiKey(), request.getApiSecret());
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
