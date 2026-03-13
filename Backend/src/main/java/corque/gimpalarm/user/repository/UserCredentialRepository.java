package corque.gimpalarm.user.repository;

import corque.gimpalarm.user.domain.User;
import corque.gimpalarm.user.domain.UserCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserCredentialRepository extends JpaRepository<UserCredential, Long> {
    List<UserCredential> findByUserId(Long userId);
    Optional<UserCredential> findByUserAndExchange(User user, String exchange);
}
