package corque.gimpalarm.user.service;

import corque.gimpalarm.common.util.JwtTokenProvider;
import corque.gimpalarm.user.domain.RefreshToken;
import corque.gimpalarm.user.domain.User;
import corque.gimpalarm.user.repository.RefreshTokenRepository;
import corque.gimpalarm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${jwt.token.refresh-expire-length}")
    private long refreshTokenValidityInMilliseconds;

    @Transactional
    public User signup(String email, String password, String nickname) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already exists");
        }

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .nickname(nickname)
                .build();

        return userRepository.save(user);
    }

    @Transactional
    public TokenResponse login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getEmail());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getEmail());

        saveRefreshToken(user.getEmail(), refreshToken);

        return new TokenResponse(accessToken, refreshToken);
    }

    @Transactional
    public void logout(String email) {
        refreshTokenRepository.deleteByEmail(email);
    }

    @Transactional
    public String refreshAccessToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new RuntimeException("Invalid refresh token");
        }

        RefreshToken storedToken = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new RuntimeException("Refresh token not found in DB"));

        if (storedToken.isExpired()) {
            refreshTokenRepository.delete(storedToken);
            throw new RuntimeException("Refresh token expired");
        }

        return jwtTokenProvider.createAccessToken(storedToken.getEmail());
    }

    private void saveRefreshToken(String email, String token) {
        LocalDateTime expiryDate = LocalDateTime.now().plusNanos(refreshTokenValidityInMilliseconds * 1_000_000);
        RefreshToken refreshToken = refreshTokenRepository.findByEmail(email)
                .orElse(RefreshToken.builder().email(email).build());
        
        refreshToken.updateToken(token, expiryDate);
        refreshTokenRepository.save(refreshToken);
    }

    public boolean isNicknameAvailable(String nickname) {
        return !userRepository.existsByNickname(nickname);
    }

    public String getNickname(String email) {
        return userRepository.findByEmail(email)
                .map(User::getNickname)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @lombok.Value
    public static class TokenResponse {
        String accessToken;
        String refreshToken;
    }
}
