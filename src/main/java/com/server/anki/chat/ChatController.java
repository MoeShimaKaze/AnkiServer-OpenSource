package com.server.anki.chat;

import com.server.anki.auth.AuthenticationService;
import com.server.anki.chat.dto.ChatDTO;
import com.server.anki.chat.entity.Chat;
import com.server.anki.chat.entity.ChatMessage;
import com.server.anki.ticket.Ticket;
import com.server.anki.ticket.TicketDTO;
import com.server.anki.ticket.TicketRepository;
import com.server.anki.user.User;
import com.server.anki.user.UserDTO;
import com.server.anki.user.UserService;  // 新增：引入 UserService
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserService userService; // 新增：注入 UserService

    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(@RequestBody ChatMessage chatMessage,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        logger.info("Received request to send message for ticket: {}", chatMessage.getTicketId());

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            logger.warn("Unauthorized access attempt to send message");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Ticket ticket;
        try {
            ticket = ticketRepository.findById(chatMessage.getTicketId()).orElseThrow();
        } catch (Exception e) {
            logger.error("Failed to find ticket. TicketId: {}", chatMessage.getTicketId(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Ticket not found.");
        }

        // 原先的 isClosedTicketAndNotAdmin(...) => 使用 userService.isAdminUser(user)
        if (!ticket.isOpen() && !userService.isAdminUser(user)) {
            logger.warn("Attempt to send message to closed ticket by non-admin. UserId: {}, TicketId: {}", user.getId(), ticket.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("工单已关闭，无法发送消息。");
        }

        Chat chat = new Chat();
        chat.setMessage(chatMessage.getMessage());
        chat.setTimestamp(LocalDateTime.now());
        chat.setUser(user);
        chat.setTicket(ticket);

        Chat savedChat = chatRepository.save(chat);
        logger.info("Message sent successfully. ChatId: {}, UserId: {}, TicketId: {}",
                savedChat.getId(), user.getId(), ticket.getId());
        return ResponseEntity.ok(savedChat);
    }

    @GetMapping("/ticket/{ticketId}")
    public ResponseEntity<?> getChatsByTicketId(@PathVariable Long ticketId,
                                                HttpServletRequest request,
                                                HttpServletResponse response) {
        logger.info("Received request to get chats for ticket: {}", ticketId);

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            logger.warn("Unauthorized access attempt to get chats");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Ticket ticket;
        try {
            ticket = ticketRepository.findById(ticketId).orElseThrow();
        } catch (Exception e) {
            logger.error("Failed to find ticket. TicketId: {}", ticketId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Ticket not found.");
        }

        if (!ticket.isOpen() && !userService.isAdminUser(user)) {
            logger.warn("Non-admin user attempt to access closed ticket chats. UserId: {}, TicketId: {}",
                    user.getId(), ticketId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("工单已关闭，无法获取聊天记录。");
        }

        List<Chat> chats = chatRepository.findByTicket(ticket);

        // 转换为 ChatDTO
        List<ChatDTO> chatDTOs = chats.stream().map(chat -> {
            ChatDTO chatDTO = new ChatDTO();
            chatDTO.setId(chat.getId());
            chatDTO.setMessage(chat.getMessage());
            chatDTO.setTimestamp(chat.getTimestamp());

            // 设置用户信息
            if (chat.getUser() != null) {
                UserDTO userDTO = new UserDTO();
                userDTO.setId(chat.getUser().getId());
                userDTO.setUsername(chat.getUser().getUsername());
                userDTO.setEmail(chat.getUser().getEmail());
                chatDTO.setUser(userDTO);
            }

            // 设置工单信息
            if (chat.getTicket() != null) {
                TicketDTO ticketDTO = new TicketDTO();
                ticketDTO.setId(chat.getTicket().getId());
                ticketDTO.setIssue(chat.getTicket().getIssue());
                chatDTO.setTicket(ticketDTO);
            }

            return chatDTO;
        }).toList();

        logger.info("Retrieved {} chats for ticket: {}", chatDTOs.size(), ticketId);
        return ResponseEntity.ok(chatDTOs);
    }
}
