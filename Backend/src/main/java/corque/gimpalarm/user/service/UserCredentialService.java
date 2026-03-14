package corque.gimpalarm.user.service;

import corque.gimpalarm.common.util.EncryptionUtil;
import corque.gimpalarm.user.domain.User;
import corque.gimpalarm.user.domain.UserCredential;
import corque.gimpalarm.user.repository.UserCredentialRepository;
import corque.gimpalarm.user.repository.UserRepository;
import corque.gimpalarm.user.dto.UserCredentialRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserCredentialService {

    private final UserCredentialRepository userCredentialRepository;
    private final UserRepository userRepository;
    private final EncryptionUtil encryptionUtil;

    @Transactional
    public Long registerCredential(UserCredentialRequestDto requestDto) {
        User user = userRepository.findById(requestDto.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + requestDto.getUserId()));

        // Encrypt keys using EncryptionUtil
        String encryptedApiKey = encryptionUtil.encrypt(requestDto.getAccessKey());
        String encryptedApiSecret = encryptionUtil.encrypt(requestDto.getSecretKey());

        UserCredential credential = UserCredential.builder()
                .user(user)
                .exchange(requestDto.getExchange())
                .apiKey(encryptedApiKey)
                .apiSecret(encryptedApiSecret)
                .build();

        return userCredentialRepository.save(credential).getId();
    }
}
