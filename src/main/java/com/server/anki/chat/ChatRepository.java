package com.server.anki.chat;

import com.server.anki.chat.entity.Chat;
import com.server.anki.ticket.Ticket;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {
    @EntityGraph(attributePaths = {"user", "ticket"})
    List<Chat> findByTicket(Ticket ticket);
}
