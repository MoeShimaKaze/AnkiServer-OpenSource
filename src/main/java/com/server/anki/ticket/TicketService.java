package com.server.anki.ticket;

import com.server.anki.chat.entity.Chat;
import com.server.anki.chat.ChatRepository;
import com.server.anki.message.service.MessageService;
import com.server.anki.message.MessageType;
import com.server.anki.user.User;
import com.server.anki.user.UserService;
import com.server.anki.websocket.HeartbeatWebSocketHandler;
import com.server.anki.websocket.NotificationWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 工单服务层，处理所有工单相关的业务逻辑
 */
@Service
public class TicketService {
    private static final Logger logger = LoggerFactory.getLogger(TicketService.class);

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private MessageService messageService;

    @Autowired
    private UserService userService;

    @Autowired
    private HeartbeatWebSocketHandler heartbeatHandler;

    @Autowired
    private NotificationWebSocketHandler notificationWebSocketHandler;

    /**
     * 创建工单
     */
    @Transactional
    public Ticket createTicket(TicketDTO ticketDto, User user) {
        logger.info("正在为用户创建工单: {}", user.getId());

        // 验证工单数据
        validateTicketFields(ticketDto);

        // 创建工单实体
        Ticket ticket = new Ticket();
        ticket.setIssue(ticketDto.getIssue());
        ticket.setType(ticketDto.getType());
        ticket.setCreatedDate(LocalDateTime.now());
        ticket.setOpen(true);
        ticket.setUser(user);

        // 自动分配管理员
        User assignedAdmin = assignAdmin();
        ticket.setAssignedAdmin(assignedAdmin);

        // 保存工单
        Ticket savedTicket = ticketRepository.save(ticket);

        // 发送工单创建通知
        sendTicketCreationNotifications(savedTicket);

        logger.info("工单创建成功: {}", savedTicket.getId());
        return savedTicket;
    }

    private User assignAdmin() {
        List<User> activeAdmins = heartbeatHandler.getActiveAdmins();
        List<User> allAdmins = userService.getAllAdmins();

        // Combine active and inactive admins, prioritizing active ones
        List<User> sortedAdmins = allAdmins.stream()
                .sorted(Comparator.comparingInt(this::getAssignedTicketCount)
                        .thenComparing(user -> !activeAdmins.contains(user)))
                .toList();

        return sortedAdmins.stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("没有可用的管理员"));
    }

    private int getAssignedTicketCount(User admin) {
        return (int) ticketRepository.findAll().stream()
                .filter(ticket -> admin.equals(ticket.getAssignedAdmin()))
                .count();
    }

    /**
     * 获取工单详情
     */
    public Ticket getTicket(Long id) {
        logger.info("获取工单信息: {}", id);
        return ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException("工单未找到: " + id));
    }

    /**
     * 关闭工单
     */
    @Transactional
    public Ticket closeTicket(Long id, User user) {
        logger.info("正在关闭工单: {}, 操作用户: {}", id, user.getId());

        Ticket ticket = getTicket(id);
        validateTicketAccess(ticket, user);

        ticket.setOpen(false);
        ticket.setClosedDate(LocalDateTime.now());
        ticket.setClosedBy(user);
        ticket.setClosedByAdmin(userService.isAdminUser(user));

        Ticket savedTicket = ticketRepository.save(ticket);
        sendTicketClosureNotifications(savedTicket, user);

        logger.info("工单关闭成功: {}", savedTicket.getId());
        return savedTicket;
    }

    /**
     * 重新打开工单
     */
    @Transactional
    public Ticket reopenTicket(Long id, User user) {
        logger.info("正在重新打开工单: {}, 操作用户: {}", id, user.getId());

        Ticket ticket = getTicket(id);

        if (ticket.isOpen()) {
            throw new IllegalStateException("工单已经处于开启状态");
        }

        if (ticket.isClosedByAdmin() && !userService.isAdminUser(user)) {
            throw new IllegalStateException("该工单已被管理员关闭，您无权重新开启");
        }

        ticket.setOpen(true);
        ticket.setClosedDate(null);
        ticket.setClosedByAdmin(false);

        Ticket savedTicket = ticketRepository.save(ticket);
        sendTicketReopenNotifications(savedTicket, user);

        logger.info("工单重新打开成功: {}", savedTicket.getId());
        return savedTicket;
    }

    /**
     * 回复工单
     */
    @Transactional
    public Chat replyTicket(TicketDTO ticketDto, User user) {
        logger.info("正在回复工单: {}, 操作用户: {}", ticketDto.getId(), user.getId());

        // 验证回复内容
        validateReplyFields(ticketDto);

        Ticket ticket = getTicket(ticketDto.getId());
        validateTicketAccess(ticket, user);

        Chat chat = new Chat();
        chat.setMessage(ticketDto.getMessage());
        chat.setTimestamp(LocalDateTime.now());
        chat.setTicket(ticket);
        chat.setUser(user);

        Chat savedChat = chatRepository.save(chat);
        sendTicketReplyNotifications(ticket, user);

        logger.info("工单回复成功: {}", savedChat.getId());
        return savedChat;
    }

    /**
     * 获取用户工单列表
     */
    public List<Ticket> getUserTickets(Long userId) {
        logger.info("获取用户工单列表: {}", userId);
        return ticketRepository.findByUserId(userId);
    }

    /**
     * 获取所有工单列表
     */
    public List<Ticket> getAllTickets() {
        logger.info("获取所有工单列表");
        return ticketRepository.findAll();
    }

    /**
     * 获取用户工单列表（分页）
     */
    public Page<Ticket> getUserTicketsPaged(Long userId, boolean open, int page, int size) {
        logger.info("获取用户工单列表（分页）: 用户ID={}, 状态={}, 页码={}, 每页大小={}", userId, open ? "开启" : "关闭", page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdDate"));
        return ticketRepository.findByUserIdAndOpen(userId, open, pageable);
    }

    /**
     * 获取所有工单列表（分页）- 按状态筛选
     */
    public Page<Ticket> getAllTicketsPaged(boolean open, int page, int size) {
        logger.info("获取所有工单列表（分页）: 状态={}, 页码={}, 每页大小={}", open ? "开启" : "关闭", page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdDate"));
        return ticketRepository.findByOpen(open, pageable);
    }

    /**
     * 获取所有工单列表（分页）- 不筛选状态
     */
    public Page<Ticket> getAllTicketsPaged(int page, int size) {
        logger.info("获取所有工单列表（分页）: 页码={}, 每页大小={}", page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdDate"));
        return ticketRepository.findAll(pageable);
    }

    /**
     * 验证工单字段
     */
    private void validateTicketFields(TicketDTO ticketDto) {
        if (!StringUtils.hasText(ticketDto.getIssue())) {
            throw new IllegalArgumentException("工单内容不能为空");
        }
        if (ticketDto.getType() <= 0) {
            throw new IllegalArgumentException("工单类型无效");
        }
    }

    /**
     * 验证回复字段
     */
    private void validateReplyFields(TicketDTO ticketDto) {
        if (!StringUtils.hasText(ticketDto.getMessage())) {
            throw new IllegalArgumentException("回复内容不能为空");
        }
    }

    /**
     * 验证工单访问权限
     */
    private void validateTicketAccess(Ticket ticket, User user) {
        if (!ticket.getUser().getId().equals(user.getId()) && !userService.isAdminUser(user)) {
            throw new IllegalStateException("没有权限操作该工单");
        }
    }

    /**
     * 发送工单创建通知
     */
    private void sendTicketCreationNotifications(Ticket ticket) {
        try {
            // 向工单创建者发送消息
            String userContent = "您的工单 #" + ticket.getId() + " 已创建";
            messageService.sendMessage(ticket.getUser(), userContent, MessageType.TICKET_STATUS_UPDATED, ticket.getId());

            // 向所有管理员发送消息
            String adminContent = "新工单 #" + ticket.getId() + " 已被用户 " + ticket.getUser().getUsername() + " 创建";
            messageService.sendMessageToAdmins(adminContent, MessageType.TICKET_STATUS_UPDATED, ticket.getId());

            // Send real-time notification to all authenticated administrators
            notificationWebSocketHandler.sendNotificationToAdmins(adminContent, ticket.getId());

            logger.info("工单创建通知发送成功, 工单ID: {}", ticket.getId());
        } catch (Exception e) {
            logger.error("发送工单创建通知时发生错误, 工单ID: {}", ticket.getId(), e);
        }
    }

    /**
     * 发送工单关闭通知
     */
    private void sendTicketClosureNotifications(Ticket ticket, User closer) {
        try {
            boolean closedByAdmin = userService.isAdminUser(closer);
            String content = String.format("工单 #%d 已被%s %s 关闭",
                    ticket.getId(),
                    closedByAdmin ? "管理员" : "用户",
                    closer.getUsername());

            // 如果是管理员关闭，给工单创建者发送通知
            if (closedByAdmin) {
                messageService.sendMessage(ticket.getUser(), content, MessageType.TICKET_STATUS_UPDATED, ticket.getId());
            }

            // 给其他管理员发送通知
            messageService.sendMessageToAdmins(content, MessageType.TICKET_STATUS_UPDATED, ticket.getId());
            logger.info("工单关闭通知发送成功, 工单ID: {}", ticket.getId());
        } catch (Exception e) {
            logger.error("发送工单关闭通知时发生错误, 工单ID: {}", ticket.getId(), e);
        }
    }

    /**
     * 发送工单重开通知
     */
    private void sendTicketReopenNotifications(Ticket ticket, User reopener) {
        try {
            boolean isAdmin = userService.isAdminUser(reopener);
            String content = String.format("工单 #%d 已被%s %s 重新打开",
                    ticket.getId(),
                    isAdmin ? "管理员" : "用户",
                    reopener.getUsername());

            // 如果是管理员重开，给工单创建者发送通知
            if (isAdmin) {
                messageService.sendMessage(ticket.getUser(), content, MessageType.TICKET_STATUS_UPDATED, ticket.getId());
            }

            // 给其他管理员发送通知
            messageService.sendMessageToAdmins(content, MessageType.TICKET_STATUS_UPDATED, ticket.getId());
            logger.info("工单重开通知发送成功, 工单ID: {}", ticket.getId());
        } catch (Exception e) {
            logger.error("发送工单重开通知时发生错误, 工单ID: {}", ticket.getId(), e);
        }
    }

    /**
     * 获取分配给指定管理员的工单列表（分页）
     * @param admin 管理员用户
     * @param open 工单状态（开启/关闭）
     * @param page 页码
     * @param size 每页大小
     * @return 工单分页结果
     */
    public Page<Ticket> getAdminAssignedTicketsPaged(User admin, boolean open, int page, int size) {
        logger.info("获取管理员工单列表（分页）: 管理员ID={}, 状态={}, 页码={}, 每页大小={}",
                admin.getId(), open ? "开启" : "关闭", page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdDate"));
        return ticketRepository.findByAssignedAdminAndOpen(admin, open, pageable);
    }

    /**
     * 获取分配给指定管理员ID的工单列表（分页）
     * @param adminId 管理员ID
     * @param open 工单状态（开启/关闭）
     * @param page 页码
     * @param size 每页大小
     * @return 工单分页结果
     */
    public Page<Ticket> getAdminAssignedTicketsPaged(Long adminId, boolean open, int page, int size) {
        logger.info("获取管理员工单列表（分页）: 管理员ID={}, 状态={}, 页码={}, 每页大小={}",
                adminId, open ? "开启" : "关闭", page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdDate"));
        return ticketRepository.findByAssignedAdminIdAndOpen(adminId, open, pageable);
    }

    /**
     * 发送工单回复通知
     */
    public void sendTicketReplyNotifications(Ticket ticket, User replier) {
        try {
            String content = String.format("用户 %s 在工单 #%d 中发送了一条消息",
                    replier.getUsername(), ticket.getId());

            // 给工单创建者发送通知（如果回复者不是创建者本人）
            if (!replier.getId().equals(ticket.getUser().getId())) {
                messageService.sendMessage(ticket.getUser(), content, MessageType.TICKET_STATUS_UPDATED, ticket.getId());
            }

            // 给管理员发送通知
            User assignedAdmin = ticket.getAssignedAdmin();
            if (assignedAdmin != null && heartbeatHandler.isUserAuthenticated(assignedAdmin.getId())) {
                // 如果分配的管理员在线，发送通知给该管理员
                messageService.sendMessage(assignedAdmin, content, MessageType.TICKET_REPLIED, ticket.getId());
                notificationWebSocketHandler.sendNotification(assignedAdmin.getId(), content, MessageType.TICKET_REPLIED.toString(), ticket.getId());
            } else {
                // 如果分配的管理员不在线，发送通知给其他在线管理员
                List<User> activeAdmins = heartbeatHandler.getActiveAdmins();
                if (!activeAdmins.isEmpty()) {
                    for (User admin : activeAdmins) {
                        messageService.sendMessage(admin, content, MessageType.TICKET_REPLIED, ticket.getId());
                        notificationWebSocketHandler.sendNotification(admin.getId(), content, MessageType.TICKET_REPLIED.toString(), ticket.getId());
                    }
                } else {
                    // 如果没有在线管理员，发送站内信给所有管理员
                    messageService.sendMessageToAdmins(content, MessageType.TICKET_REPLIED, ticket.getId());
                }
            }
            logger.info("工单回复通知发送成功, 工单ID: {}", ticket.getId());
        } catch (Exception e) {
            logger.error("发送工单回复通知时发生错误, 工单ID: {}", ticket.getId(), e);
        }
    }

    public Ticket save(Ticket ticket) {
        return ticketRepository.save(ticket);
    }
}