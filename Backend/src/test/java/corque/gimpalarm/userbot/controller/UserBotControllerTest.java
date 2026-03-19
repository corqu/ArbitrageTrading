package corque.gimpalarm.userbot.controller;

import corque.gimpalarm.coin.dto.TradingRequest;
import corque.gimpalarm.user.domain.UserPrincipal;
import corque.gimpalarm.userbot.dto.UserBotResponseDto;
import corque.gimpalarm.userbot.service.UserBotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserBotControllerTest {

    @Mock
    private UserBotService userBotService;

    private UserBotController userBotController;

    @BeforeEach
    void setUp() {
        userBotController = new UserBotController(userBotService);
    }

    @Test
    void getMyBotsReturnsServiceResult() {
        UserDetails userDetails = User.withUsername("user@test.com").password("pw").authorities(List.of()).build();
        List<UserBotResponseDto> bots = List.of(UserBotResponseDto.builder().id(10L).symbol("BTC").build());
        when(userBotService.getMyBots("user@test.com")).thenReturn(bots);

        var response = userBotController.getMyBots(userDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        verify(userBotService).getMyBots("user@test.com");
    }

    @Test
    void subscribeBotReturnsCreatedBot() {
        UserPrincipal principal = UserPrincipal.builder()
                .id(1L)
                .email("user@test.com")
                .password("pw")
                .nickname("nick")
                .build();
        TradingRequest request = new TradingRequest();
        UserBotResponseDto dto = UserBotResponseDto.builder().id(10L).symbol("BTC").build();
        when(userBotService.subscribeBot(1L, request)).thenReturn(dto);

        var response = userBotController.subscribeBot(principal, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(10L, response.getBody().getId());
        verify(userBotService).subscribeBot(1L, request);
    }

    @Test
    void deleteBotReturnsNoContent() {
        UserDetails userDetails = User.withUsername("user@test.com").password("pw").authorities(List.of()).build();

        var response = userBotController.deleteBot(userDetails, 10L);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(userBotService).deleteBot("user@test.com", 10L);
    }
}
