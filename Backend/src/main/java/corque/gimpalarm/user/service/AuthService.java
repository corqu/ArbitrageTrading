package corque.gimpalarm.user.service;

import corque.gimpalarm.common.util.EncryptionUtil;
import corque.gimpalarm.common.util.JwtTokenProvider;
import corque.gimpalarm.user.domain.User;
import corque.gimpalarm.user.domain.UserCredential;
import corque.gimpalarm.user.repository.UserCredentialRepository;
import corque.gimpalarm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserCredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EncryptionUtil encryptionUtil;

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

    public String login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        return jwtTokenProvider.createToken(user.getEmail());
    }

    @Transactional
    public void saveSecrets(String email, String exchange, String apiKey, String apiSecret) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String encryptedApiKey = encryptionUtil.encrypt(apiKey);
        String encryptedApiSecret = encryptionUtil.encrypt(apiSecret);

        UserCredential credential = credentialRepository.findByUserAndExchange(user, exchange)
                .orElse(UserCredential.builder()
                        .user(user)
                        .exchange(exchange)
                        .build());

        credential.setApiKey(encryptedApiKey);
        credential.setApiSecret(encryptedApiSecret);

        credentialRepository.save(credential);
    }
}
