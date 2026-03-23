package corque.gimpalarm.userbot.service;

import corque.gimpalarm.coin.dto.TradingRequest;
import corque.gimpalarm.coin.service.TradingBotService;
import corque.gimpalarm.common.exception.NotFoundException;
import corque.gimpalarm.user.domain.User;
import corque.gimpalarm.user.repository.UserRepository;
import corque.gimpalarm.userbot.domain.UserBot;
import corque.gimpalarm.userbot.domain.UserBotStatus;
import corque.gimpalarm.userbot.dto.UserBotResponseDto;
import corque.gimpalarm.userbot.repository.UserBotRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserBotService {
    private static final double DEFAULT_STOP_LOSS_BUFFER_PERCENT = 80.0;

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
                .orElseThrow(() -> new NotFoundException("User not found"));

        return userBotRepository.findAllByUser(user).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserBotResponseDto subscribeBot(Long userId, TradingRequest request) {
        applyRiskDefaults(request);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

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
                .status(UserBotStatus.WAITING)
                .build();

        UserBot savedBot = userBotRepository.save(userBot);
        tradingBotService.executeTradeForUser(userId, request);
        return convertToDto(savedBot);
    }

    @Transactional
    public UserBotResponseDto updateBot(Long userId, Long botId, TradingRequest request) {
        applyRiskDefaults(request);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        UserBot userBot = userBotRepository.findByIdAndUser(botId, user)
                .orElseThrow(() -> new NotFoundException("Bot not found or unauthorized"));

        stopBotInMemory(user, userBot);

        userBot.setAmountKrw(request.getAmountKrw());
        userBot.setLeverage(request.getLeverage());
        userBot.setEntryKimp(request.getEntryKimp());
        userBot.setExitKimp(request.getExitKimp());
        userBot.setStopLossPercent(request.getStopLossPercent());
        userBot.setTakeProfitPercent(request.getTakeProfitPercent());
        userBot.setStatus(UserBotStatus.WAITING);

        tradingBotService.executeTradeForUser(userId, request);
        return convertToDto(userBot);
    }

    @Transactional
    public void deleteBot(String email, Long botId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found"));

        UserBot userBot = userBotRepository.findByIdAndUser(botId, user)
                .orElseThrow(() -> new NotFoundException("Bot not found or unauthorized"));

        stopBotInMemory(user, userBot);
        userBotRepository.delete(userBot);
    }

    private void stopBotInMemory(User user, UserBot userBot) {
        TradingRequest stopRequest = new TradingRequest();
        stopRequest.setSymbol(userBot.getSymbol());
        stopRequest.setDomesticExchange(userBot.getDomesticExchange());
        stopRequest.setForeignExchange(userBot.getForeignExchange());
        stopRequest.setAction("STOP");

        userBot.setStatus(UserBotStatus.STOPPED);
        tradingBotService.executeTradeForUser(user.getId(), stopRequest);
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
                .status(userBot.getStatus())
                .build();
    }

    private void applyRiskDefaults(TradingRequest request) {
        if (request == null) {
            return;
        }

        if (request.getStopLossPercent() == null) {
            request.setStopLossPercent(DEFAULT_STOP_LOSS_BUFFER_PERCENT);
        }
        request.setTakeProfitPercent(null);
    }
}
