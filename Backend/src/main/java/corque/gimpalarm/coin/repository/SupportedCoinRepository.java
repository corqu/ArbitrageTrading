package corque.gimpalarm.coin.repository;

import corque.gimpalarm.coin.domain.SupportedCoin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupportedCoinRepository extends JpaRepository<SupportedCoin, Long> {
    Optional<SupportedCoin> findBySymbol(String symbol);
}
