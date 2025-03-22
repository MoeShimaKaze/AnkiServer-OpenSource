package com.server.anki.user;

import com.server.anki.user.enums.UserIdentity;
import com.server.anki.user.enums.UserVerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    List<User> findByUserGroup(String userGroup);
    Optional<User> findBySystemAccount(boolean systemAccount);
    // 添加分页查询方法
    Page<User> findByUserVerificationStatus(UserVerificationStatus userVerificationStatus, Pageable pageable);
    List<User> findByUserIdentity(UserIdentity userIdentity);
    // 根据 JSON 字段查询 idNumber
    @Query("SELECT u FROM User u WHERE JSON_UNQUOTE(JSON_EXTRACT(u.personalInfo, '$.id_number')) = :idNumber")
    Optional<User> findByIdNumberInPersonalInfo(@Param("idNumber") String idNumber);

    // 根据 JSON 字段查询 studentId
    @Query("SELECT u FROM User u WHERE JSON_UNQUOTE(JSON_EXTRACT(u.personalInfo, '$.student_id')) = :studentId")
    Optional<User> findByStudentIdInPersonalInfo(@Param("studentId") String studentId);

    // 根据 JSON 字段查询 socialCreditCode
    @Query("SELECT u FROM User u WHERE JSON_UNQUOTE(JSON_EXTRACT(u.personalInfo, '$.social_credit_code')) = :socialCreditCode")
    Optional<User> findBySocialCreditCodeInPersonalInfo(@Param("socialCreditCode") String socialCreditCode);
    // 修改为只查询有效的支付宝用户ID
    @Query("SELECT u FROM User u WHERE u.alipayUserId = :alipayUserId")
    Optional<User> findByAlipayUserId(@Param("alipayUserId") String alipayUserId);

    // 检查支付宝用户ID是否已被使用
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.alipayUserId = :alipayUserId")
    boolean existsByAlipayUserId(@Param("alipayUserId") String alipayUserId);

    /**
     * 根据用户组查询用户（分页）
     * @param userGroup 用户组名称
     * @param pageable 分页请求
     * @return 用户分页结果
     */
    Page<User> findByUserGroup(String userGroup, Pageable pageable);
}