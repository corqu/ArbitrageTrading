package corque.gimpalarm.userbot.service;

import corque.gimpalarm.user.domain.User;
import corque.gimpalarm.user.repository.UserRepository;
import corque.gimpalarm.userbot.domain.UserBot;
import corque.gimpalarm.userbot.dto.UserBotResponseDto;
import corque.gimpalarm.userbot.repository.UserBotRepository;
import corque.gimpalarm.coin.dto.TradingRequest;
import corque.gimpalarm.coin.service.TradingBotService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserBotService {

    private final UserBotRepository userBotRepository;
    private final UserRepository userRepository;
    private final TradingBotService tradingBotService;

    @PostConstruct
    public void init() {
        List<UserBot> activeBots = userBotRepository.findAllByIsActiveTrue();
        tradingBotService.loadActiveBots(activeBots);
    }

    @Transactional(readOnly = true)
    public List<UserBotResponseDto> getMyBots(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        return userBotRepository.findAllByUser(user).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserBotResponseDto subscribeBot(String email, TradingRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserBot userBot = UserBot.builder()
                .user(user)
                .symbol(request.getSymbol())
                .domesticExchange(request.getDomesticExchange())
                .foreignExchange(request.getForeignExchange())
                .amountKrw(request.getAmountKrw())
                .leverage(request.getLeverage())
                .action(request.getAction())
                .entryKimp(request.getEntryKimp())
                .exitKimp(request.getExitKimp())
                .stopLossPercent(request.getStopLossPercent())
                .takeProfitPercent(request.getTakeProfitPercent())
                .isActive(true)
                .build();

        UserBot savedBot = userBotRepository.save(userBot);
        
        // 실시간 매매 서비스에 봇 실행 요청
        tradingBotService.executeTradeForUser(user, request);

        return convertToDto(savedBot);
    }

    @Transactional
    public UserBotResponseDto updateBot(String email, Long botId, TradingRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        UserBot userBot = userBotRepository.findByIdAndUser(botId, user)
                .orElseThrow(() -> new RuntimeException("Bot not found or unauthorized"));

        // 기존 봇 중단 (메모리에서 제거)
        stopBotInMemory(user, userBot);

        // 값 업데이트
        userBot.setAmountKrw(request.getAmountKrw());
        userBot.setLeverage(request.getLeverage());
        userBot.setEntryKimp(request.getEntryKimp());
        userBot.setExitKimp(request.getExitKimp());
        
        // 다시 실행
        tradingBotService.executeTradeForUser(user, request);

        return convertToDto(userBot);
    }

    @Transactional
    public void deleteBot(String email, Long botId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        UserBot userBot = userBotRepository.findByIdAndUser(botId, user)
                .orElseThrow(() -> new RuntimeException("Bot not found or unauthorized"));

        // 메모리에서 봇 중단
        stopBotInMemory(user, userBot);
        
        userBotRepository.delete(userBot);
    }

    private void stopBotInMemory(User user, UserBot userBot) {
        TradingRequest stopRequest = new TradingRequest();
        stopRequest.setSymbol(userBot.getSymbol());
        stopRequest.setDomesticExchange(userBot.getDomesticExchange());
        stopRequest.setForeignExchange(userBot.getForeignExchange());
        stopRequest.setAction("STOP");
        
        tradingBotService.executeTradeForUser(user, stopRequest);
    }

    private UserBotResponseDto convertToDto(UserBot userBot) {
        return UserBotResponseDto.builder()
                .id(userBot.getId())
                .symbol(userBot.getSymbol())
                .domesticExchange(userBot.getDomesticExchange())
                .foreignExchange(userBot.getForeignExchange())
                .amountKrw(userBot.getAmountKrw())
                .leverage(userBot.getLeverage())
                .action(userBot.getAction())
                .entryKimp(userBot.getEntryKimp())
                .exitKimp(userBot.getExitKimp())
                .stopLossPercent(userBot.getStopLossPercent())
                .takeProfitPercent(userBot.getTakeProfitPercent())
                .isActive(userBot.isActive())
                .build();
    }
}
