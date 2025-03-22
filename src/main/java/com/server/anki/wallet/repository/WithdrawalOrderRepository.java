package com.server.anki.wallet.repository;

import com.server.anki.user.User;
import com.server.anki.wallet.entity.WithdrawalOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WithdrawalOrderRepository extends JpaRepository<WithdrawalOrder, Long> {
    Optional<WithdrawalOrder> findByOrderNumber(String orderNumber);
    List<WithdrawalOrder> findByUserOrderByCreatedTimeDesc(User user);
    List<WithdrawalOrder> findByUserAndStatusOrderByCreatedTimeDesc(User user, WithdrawalOrder.WithdrawalStatus status);
}