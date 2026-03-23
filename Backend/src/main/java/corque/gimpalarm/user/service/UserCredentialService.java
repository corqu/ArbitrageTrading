package corque.gimpalarm.user.service;

import corque.gimpalarm.common.exception.NotFoundException;
import corque.gimpalarm.common.util.EncryptionUtil;
import corque.gimpalarm.user.domain.User;
import corque.gimpalarm.user.domain.UserCredential;
import corque.gimpalarm.user.repository.UserCredentialRepository;
import corque.gimpalarm.user.repository.UserRepository;
import corque.gimpalarm.user.dto.UserCredentialRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserCredentialService {

    private final UserCredentialRepository userCredentialRepository;
    private final UserRepository userRepository;
    private final EncryptionUtil encryptionUtil;

    @Transactional(readOnly = true)
    public List<String> getUserCredentials(Long userId) {
        return userCredentialRepository.findByUserId(userId).stream()
                .map(UserCredential::getExchange)
                .collect(Collectors.toList());
    }

    @Transactional
    public Long registerCredential(Long userId, UserCredentialRequestDto requestDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        String encryptedApiKey = encryptionUtil.encrypt(requestDto.getAccessKey());
        String encryptedApiSecret = encryptionUtil.encrypt(requestDto.getSecretKey());

        UserCredential credential = userCredentialRepository.findByUserAndExchange(user, requestDto.getExchange())
                .orElse(UserCredential.builder()
                        .user(user)
                        .exchange(requestDto.getExchange())
                        .build());

        credential.setApiKey(encryptedApiKey);
        credential.setApiSecret(encryptedApiSecret);

        return userCredentialRepository.save(credential).getId();
    }

    @Transactional
    public void deleteCredential(Long userId, String exchange) {
        userCredentialRepository.deleteByUserIdAndExchange(userId, exchange);
    }
}
