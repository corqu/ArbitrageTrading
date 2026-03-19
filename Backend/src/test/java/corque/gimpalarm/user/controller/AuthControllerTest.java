package corque.gimpalarm.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import corque.gimpalarm.common.exception.GlobalExceptionHandler;
import corque.gimpalarm.common.util.JwtTokenProvider;
import corque.gimpalarm.user.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, jwtTokenProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void signupReturnsOk() throws Exception {
        Map<String, String> request = Map.of(
                "email", "user@test.com",
                "password", "pw",
                "nickname", "nick"
        );

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(authService).signup("user@test.com", "pw", "nick");
    }

    @Test
    void loginReturnsCookies() throws Exception {
        Map<String, String> request = Map.of(
                "email", "user@test.com",
                "password", "pw"
        );
        when(authService.login("user@test.com", "pw"))
                .thenReturn(new AuthService.TokenResponse("access-token", "refresh-token"));
        when(jwtTokenProvider.createAccessTokenCookie("access-token"))
                .thenReturn(ResponseCookie.from("accessToken", "access-token").path("/").build());
        when(jwtTokenProvider.createRefreshTokenCookie("refresh-token"))
                .thenReturn(ResponseCookie.from("refreshToken", "refresh-token").path("/").build());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE,
                        "accessToken=access-token; Path=/",
                        "refreshToken=refresh-token; Path=/"));
    }

    @Test
    void refreshReturns401WhenRefreshCookieMissing() throws Exception {

        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void checkNicknameReturnsAvailability() throws Exception {
        when(authService.isNicknameAvailable("nick")).thenReturn(true);

        mockMvc.perform(get("/api/auth/check-nickname").param("nickname", "nick"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }
}

