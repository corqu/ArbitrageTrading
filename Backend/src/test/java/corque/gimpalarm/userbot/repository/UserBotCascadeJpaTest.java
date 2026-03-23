package corque.gimpalarm.userbot.repository;

import corque.gimpalarm.botstate.domain.BotTradeState;
import corque.gimpalarm.botstate.repository.BotTradeStateRepository;
import corque.gimpalarm.coin.domain.BotStatus;
import corque.gimpalarm.user.domain.User;
import corque.gimpalarm.user.repository.UserRepository;
import corque.gimpalarm.userbot.domain.UserBot;
import corque.gimpalarm.userbot.domain.UserBotStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class UserBotCascadeJpaTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserBotRepository userBotRepository;
    @Autowired
    private BotTradeStateRepository botTradeStateRepository;

    @Test
    void deletingUserBotAlsoDeletesBotTradeState() {
        User user = userRepository.save(User.builder()
                .email("user@test.com")
                .password("pw")
                .nickname("nick")
                .build());

        UserBot userBot = userBotRepository.save(UserBot.builder()
                .user(user)
                .symbol("BTC")
                .domesticExchange("UPBIT")
                .foreignExchange("BINANCE_FUTURES")
                .amountKrw(100000.0)
                .leverage(3)
                .action("START")
                .status(UserBotStatus.WAITING)
                .build());

        BotTradeState state = BotTradeState.builder()
                .user(user)
                .botKey("1:BTC:UPBIT:BINANCE_FUTURES")
                .status(BotStatus.WAITING)
                .build();
        state.setUserBot(userBot);
        state = botTradeStateRepository.save(state);

        assertTrue(botTradeStateRepository.findById(state.getId()).isPresent());

        userBotRepository.delete(userBot);
        userBotRepository.flush();

        assertFalse(userBotRepository.findById(userBot.getId()).isPresent());
        assertFalse(botTradeStateRepository.findById(state.getId()).isPresent());
    }
}
