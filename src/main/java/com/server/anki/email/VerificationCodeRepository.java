package com.server.anki.email;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {

    Optional<VerificationCode> findByEmail(String email);
    Optional<VerificationCode> findByEmailAndVerificationCode(String email, String verificationCode);
    List<VerificationCode> findFirstByEmailOrderBySendTimeDesc(String email);

    @Transactional
    void deleteBySendTimeBefore(LocalDateTime time);
}
