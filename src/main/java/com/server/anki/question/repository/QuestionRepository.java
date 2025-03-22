package com.server.anki.question.repository;

import com.server.anki.question.entity.Question;
import com.server.anki.question.enums.QuestionStatus;
import com.server.anki.question.enums.QuestionType;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * 问题仓库接口
 */
@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    // 按用户ID查询问题列表 - 修改为支持分页
    Page<Question> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // 按状态查询问题列表
    List<Question> findByStatusOrderByCreatedAtDesc(QuestionStatus status);

    // 分页查询问题列表
    Page<Question> findByStatus(QuestionStatus status, Pageable pageable);

    // 按问题类型查询
    List<Question> findByQuestionTypeOrderByCreatedAtDesc(QuestionType questionType);

    // 按被接受用户查询 - 修改为支持分页
    Page<Question> findByAcceptedUserIdOrderByCreatedAtDesc(Long acceptedUserId, Pageable pageable);

    // 查询用户是否已回复
    @Query("SELECT COUNT(r) > 0 FROM QuestionReply r WHERE r.question.id = :questionId AND r.user.id = :userId")
    boolean hasUserReplied(@Param("questionId") Long questionId, @Param("userId") Long userId);

    // 统计用户提问数量
    @Query("SELECT COUNT(q) FROM Question q WHERE q.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    // 统计用户解决的问题数量
    @Query("SELECT COUNT(q) FROM Question q WHERE q.acceptedUser.id = :userId AND q.status = 'RESOLVED'")
    long countResolvedQuestionsByUserId(@Param("userId") Long userId);

    // 按创建时间倒序获取最新问题
    List<Question> findTop10ByOrderByCreatedAtDesc();

    // 获取热门问题（按查看次数）
    List<Question> findTop10ByOrderByViewCountDesc();

    @NotNull Page<Question> findAll(@NotNull Pageable pageable);

    // 搜索问题
    @Query("SELECT q FROM Question q WHERE LOWER(q.description) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Question> searchQuestions(@Param("keyword") String keyword);
}