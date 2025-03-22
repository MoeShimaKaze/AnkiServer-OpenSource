package com.server.anki.question.repository;

import com.server.anki.question.entity.Question;
import com.server.anki.question.entity.QuestionReply;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 问题回复仓库接口
 */
@Repository
public interface QuestionReplyRepository extends JpaRepository<QuestionReply, Long> {
    // 获取问题的所有回复
    List<QuestionReply> findByQuestionIdOrderByCreatedAtDesc(Long questionId);

    // 获取用户对问题的回复
    Optional<QuestionReply> findByQuestionIdAndUserId(Long questionId, Long userId);

    // 获取用户的所有回复
    List<QuestionReply> findByUserIdOrderByCreatedAtDesc(Long userId);


    // 新增 - 分页获取问题回复
    Page<QuestionReply> findByQuestionIdOrderByCreatedAtDesc(Long questionId, Pageable pageable);


    // 获取申请解决的回复
    List<QuestionReply> findByQuestionIdAndAppliedTrue(Long questionId);

    // 统计用户回复数量
    @Query("SELECT COUNT(r) FROM QuestionReply r WHERE r.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    // 统计问题回复数量
    @Query("SELECT COUNT(r) FROM QuestionReply r WHERE r.question.id = :questionId")
    long countByQuestionId(@Param("questionId") Long questionId);

    // 获取最新回复
    List<QuestionReply> findTop10ByOrderByCreatedAtDesc();

    // 获取用户申请解决的回复
    List<QuestionReply> findByUserIdAndAppliedTrueOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Query("DELETE FROM QuestionReply r WHERE r.question.id = :questionId")
    void deleteByQuestionId(@Param("questionId") Long questionId);

    // 批量删除回复
    @Modifying
    @Query("DELETE FROM QuestionReply r WHERE r.id IN :ids")
    void deleteByIds(@Param("ids") List<Long> ids);

    @Transactional
    void deleteByQuestion(Question question);

}
