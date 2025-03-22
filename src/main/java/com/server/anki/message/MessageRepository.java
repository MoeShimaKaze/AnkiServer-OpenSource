package com.server.anki.message;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    // 保留原方法
    List<Message> findByUserIdOrderByCreatedDateDesc(Long userId);

    // 添加分页查询方法
    Page<Message> findByUserId(Long userId, Pageable pageable);

    // 更新方法名以匹配实体类的属性名
    List<Message> findByUserIdAndReadOrderByCreatedDateDesc(Long userId, boolean read);
}