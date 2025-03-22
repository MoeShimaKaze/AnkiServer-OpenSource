package com.server.anki.wallet.repository;

import com.server.anki.wallet.entity.WalletAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Repository
public interface WalletAuditRepository extends JpaRepository<WalletAudit, Long> {

    @Query("""
        SELECT COALESCE(SUM(w.amount), 0)
        FROM WalletAudit w
        WHERE w.reason LIKE 'Timeout fee%'
        AND w.additionalInfo = :orderId
        """)
    BigDecimal findTimeoutFeesByOrderId(String orderId);

    @Query("""
        SELECT COALESCE(SUM(w.amount), 0)
        FROM WalletAudit w
        WHERE w.reason LIKE 'Timeout fee%'
        AND w.timestamp BETWEEN :startTime AND :endTime
        """)
    BigDecimal sumTimeoutFeesBetween(LocalDateTime startTime, LocalDateTime endTime);

    @Query("""
        SELECT COALESCE(SUM(w.amount), 0)
        FROM WalletAudit w
        WHERE w.reason LIKE 'Timeout fee%'
        AND w.userId = :userId
        AND w.timestamp BETWEEN :startTime AND :endTime
        """)
    BigDecimal sumUserTimeoutFees(Long userId, LocalDateTime startTime, LocalDateTime endTime);
}