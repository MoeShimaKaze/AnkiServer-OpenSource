package com.server.anki.wallet.repository;

import com.server.anki.user.User;
import com.server.anki.wallet.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByUser(User user);
    List<Wallet> findAllByPendingBalanceGreaterThanAndPendingBalanceReleaseTimeBefore(BigDecimal amount, LocalDateTime time);
}