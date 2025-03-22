package com.server.anki.message.service;

import com.server.anki.message.Message;
import com.server.anki.message.MessageRepository;
import com.server.anki.message.MessageType;
import com.server.anki.user.User;
import com.server.anki.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MessageService {
    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private MessageProducerService messageProducer;

    public void sendMessage(User user, String content, MessageType type, Long ticketId) {
        logger.info("发送消息给用户: {}, 类型: {}", user.getId(), type);
        messageProducer.sendMessage(user, content, type, ticketId);
    }

    public void sendMessageToAdmins(String content, MessageType type, Long ticketId) {
        logger.info("发送消息给所有管理员");
        List<User> admins = userService.getAllAdmins();
        logger.debug("找到 {} 个管理员用户", admins.size());
        for (User admin : admins) {
            messageProducer.sendMessage(admin, content, type, ticketId);
        }
    }

    // 新增分页查询方法
    public Page<Message> getUserMessagesWithPagination(Long userId, Pageable pageable) {
        logger.info("获取用户 {} 的分页消息, 页码: {}, 每页大小: {}",
                userId, pageable.getPageNumber(), pageable.getPageSize());

        Page<Message> messagePage = messageRepository.findByUserId(userId, pageable);
        logger.debug("找到 {} 条消息, 总页数: {}",
                messagePage.getNumberOfElements(), messagePage.getTotalPages());

        return messagePage;
    }

    public List<Message> getUnreadMessages(Long userId) {
        logger.info("获取用户 {} 的未读消息", userId);
        List<Message> unreadMessages = messageRepository.findByUserIdAndReadOrderByCreatedDateDesc(userId, false);
        logger.debug("找到 {} 条未读消息", unreadMessages.size());
        return unreadMessages;
    }

    @Transactional
    public void markAsRead(Long messageId) {
        logger.info("标记消息 {} 为已读", messageId);
        messageRepository.findById(messageId).ifPresentOrElse(
                message -> {
                    message.setRead(true);
                    messageRepository.save(message);
                    logger.debug("消息已标记为已读");
                },
                () -> logger.warn("未找到消息: {}", messageId)
        );
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        logger.info("标记用户 {} 的所有消息为已读", userId);
        List<Message> unreadMessages = getUnreadMessages(userId);
        logger.debug("找到 {} 条未读消息需要标记", unreadMessages.size());
        unreadMessages.forEach(message -> {
            message.setRead(true);
            messageRepository.save(message);
        });
        logger.info("所有消息已标记为已读");
    }
}