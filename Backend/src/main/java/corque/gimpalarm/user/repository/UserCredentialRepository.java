package corque.gimpalarm.user.repository;

import corque.gimpalarm.user.domain.UserCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserCredentialRepository extends JpaRepository<UserCredential, Long> {
    List<UserCredential> findByUserId(Long userId);
}
