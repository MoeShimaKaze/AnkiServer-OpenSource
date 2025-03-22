package com.server.anki.message;

import com.server.anki.auth.AuthenticationService;
import com.server.anki.message.service.MessageService;
import com.server.anki.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/messages")
public class MessageController {

    private static final Logger logger = LoggerFactory.getLogger(MessageController.class);

    @Autowired
    private MessageService messageService;

    @Autowired
    private AuthenticationService authenticationService;

    @GetMapping
    public ResponseEntity<?> getUserMessages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到获取用户消息的分页请求, 页码: {}, 每页大小: {}", page, size);
        User user = authenticationService.getAuthenticatedUser(request, response);

        if (user == null) {
            logger.warn("未授权的访问尝试获取用户消息");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        logger.info("正在为用户获取分页消息: {}", user.getId());

        // 创建分页请求，按创建时间降序排列
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdDate").descending());
        Page<Message> messagePage = messageService.getUserMessagesWithPagination(user.getId(), pageable);

        // 构造响应对象
        Map<String, Object> response_data = new HashMap<>();
        response_data.put("messages", messagePage.getContent());
        response_data.put("currentPage", messagePage.getNumber());
        response_data.put("totalItems", messagePage.getTotalElements());
        response_data.put("totalPages", messagePage.getTotalPages());

        logger.info("为用户检索到分页消息: 当前页: {}, 总页数: {}, 总消息数: {}",
                messagePage.getNumber(), messagePage.getTotalPages(), messagePage.getTotalElements());

        return ResponseEntity.ok(response_data);
    }

    @GetMapping("/unread")
    public ResponseEntity<?> getUnreadMessages(HttpServletRequest request, HttpServletResponse response) {
        logger.info("收到获取未读消息的请求");
        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            logger.warn("未授权的访问尝试获取未读消息");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        logger.info("正在为用户获取未读消息: {}", user.getId());
        List<Message> unreadMessages = messageService.getUnreadMessages(user.getId());
        logger.info("为用户检索到 {} 条未读消息: {}", unreadMessages.size(), user.getId());
        return ResponseEntity.ok(unreadMessages);
    }

    @PostMapping("/{messageId}/read")
    public ResponseEntity<?> markAsRead(
            @PathVariable Long messageId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到标记消息为已读的请求: {}", messageId);
        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            logger.warn("未授权的访问尝试标记消息为已读: {}", messageId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        logger.info("正在为用户标记消息为已读: {}，消息ID: {}", user.getId(), messageId);
        messageService.markAsRead(messageId);

        // 返回更新后的分页消息
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdDate").descending());
        Page<Message> messagePage = messageService.getUserMessagesWithPagination(user.getId(), pageable);

        Map<String, Object> response_data = new HashMap<>();
        response_data.put("messages", messagePage.getContent());
        response_data.put("currentPage", messagePage.getNumber());
        response_data.put("totalItems", messagePage.getTotalElements());
        response_data.put("totalPages", messagePage.getTotalPages());

        logger.info("返回用户的更新消息列表: {}", user.getId());
        return ResponseEntity.ok(response_data);
    }

    @PostMapping("/read-all")
    public ResponseEntity<?> markAllAsRead(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到标记所有消息为已读的请求");
        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            logger.warn("未授权的访问尝试标记所有消息为已读");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        logger.info("正在为用户标记所有消息为已读: {}", user.getId());
        messageService.markAllAsRead(user.getId());

        // 返回更新后的分页消息
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdDate").descending());
        Page<Message> messagePage = messageService.getUserMessagesWithPagination(user.getId(), pageable);

        Map<String, Object> response_data = new HashMap<>();
        response_data.put("messages", messagePage.getContent());
        response_data.put("currentPage", messagePage.getNumber());
        response_data.put("totalItems", messagePage.getTotalElements());
        response_data.put("totalPages", messagePage.getTotalPages());

        logger.info("返回用户的更新消息列表: {}", user.getId());
        return ResponseEntity.ok(response_data);
    }
}