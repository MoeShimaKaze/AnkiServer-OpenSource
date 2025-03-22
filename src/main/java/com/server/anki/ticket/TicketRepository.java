package com.server.anki.ticket;

import com.server.anki.user.User;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    // 原有方法
    @EntityGraph(attributePaths = {"user", "chats.user"})
    List<Ticket> findByUserId(Long userId);
    List<Ticket> findByOpen(boolean open);

    // 新增分页查询方法
    @EntityGraph(attributePaths = {"user", "chats.user"})
    Page<Ticket> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "chats.user"})
    Page<Ticket> findByOpen(boolean open, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "chats.user"})
    Page<Ticket> findByUserIdAndOpen(Long userId, boolean open, Pageable pageable);

    @NotNull
    @EntityGraph(attributePaths = {"user", "chats.user"})
    Page<Ticket> findAll(@NotNull Pageable pageable);

    // 新增分页查询方法
    @EntityGraph(attributePaths = {"user", "chats.user"})
    Page<Ticket> findByAssignedAdminAndOpen(User admin, boolean open, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "chats.user"})
    Page<Ticket> findByAssignedAdminIdAndOpen(Long adminId, boolean open, Pageable pageable);
}