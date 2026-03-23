package corque.gimpalarm.user.service;

import corque.gimpalarm.common.exception.ConflictException;
import corque.gimpalarm.common.exception.ErrorCode;
import corque.gimpalarm.common.exception.NotFoundException;
import corque.gimpalarm.common.exception.UnauthorizedException;
import corque.gimpalarm.common.util.JwtTokenProvider;
import corque.gimpalarm.user.domain.RefreshToken;
import corque.gimpalarm.user.domain.User;
import corque.gimpalarm.user.repository.RefreshTokenRepository;
import corque.gimpalarm.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshTokenValidityInMilliseconds", 1209600000L);
    }

    @Test
    void signupThrowsWhenEmailAlreadyExists() {
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(User.builder().build()));

        assertThrows(ConflictException.class, () -> authService.signup("test@test.com", "pw", "nick"));
    }

    @Test
    void loginThrowsWhenUserMissing() {
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> authService.login("test@test.com", "pw"));
    }

    @Test
    void loginThrowsWhenPasswordDoesNotMatch() {
        User user = User.builder().email("test@test.com").password("encoded").build();
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "encoded")).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> authService.login("test@test.com", "pw"));
    }

    @Test
    void refreshAccessTokenThrowsForExpiredToken() {
        RefreshToken token = RefreshToken.builder()
                .email("test@test.com")
                .token("refresh")
                .expiryDate(LocalDateTime.now().minusMinutes(1))
                .build();
        when(jwtTokenProvider.validateToken("refresh")).thenReturn(true);
        when(refreshTokenRepository.findByToken("refresh")).thenReturn(Optional.of(token));

        UnauthorizedException exception = assertThrows(UnauthorizedException.class,
                () -> authService.refreshAccessToken("refresh"));

        assertEquals(ErrorCode.TOKEN_INVALID, exception.getErrorCode());
        verify(refreshTokenRepository).delete(token);
    }
}
