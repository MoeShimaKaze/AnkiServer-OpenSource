package com.server.anki.ticket;

import com.server.anki.auth.AuthenticationService;
import com.server.anki.chat.entity.Chat;
import com.server.anki.chat.dto.ChatDTO;
import com.server.anki.user.User;
import com.server.anki.user.UserDTO;
import com.server.anki.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工单控制器，处理所有工单相关的HTTP请求
 */
@RestController
@RequestMapping("/tickets")
public class TicketController {

    private static final Logger logger = LoggerFactory.getLogger(TicketController.class);

    @Autowired
    private TicketService ticketService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserService userService;

    /**
     * 创建工单
     */
    @PostMapping("/create")
    public ResponseEntity<?> createTicket(@RequestBody TicketDTO ticketDto,
                                          HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        logger.info("收到创建工单请求");

        User user = authenticationService.getAuthenticatedUser(httpRequest, httpResponse);
        if (user == null) {
            logger.warn("未授权的工单创建请求");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            Ticket ticket = ticketService.createTicket(ticketDto, user);
            return ResponseEntity.ok(convertToDTO(ticket));
        } catch (IllegalArgumentException e) {
            logger.warn("创建工单参数验证失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("创建工单时发生错误", e);
            return ResponseEntity.internalServerError().body("创建工单失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定工单
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getTicketById(@PathVariable Long id,
                                           HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        logger.info("收到获取工单请求，ID: {}", id);

        User user = authenticationService.getAuthenticatedUser(httpRequest, httpResponse);
        if (user == null) {
            logger.warn("未授权的工单访问请求");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            Ticket ticket = ticketService.getTicket(id);
            return ResponseEntity.ok(convertToDTO(ticket));
        } catch (TicketNotFoundException e) {
            logger.warn("工单未找到: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("工单未找到");
        }
    }

    /**
     * 关闭工单
     */
    @PostMapping("/close/{id}")
    public ResponseEntity<?> closeTicket(@PathVariable Long id,
                                         HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        logger.info("收到关闭工单请求，ID: {}", id);

        User user = authenticationService.getAuthenticatedUser(httpRequest, httpResponse);
        if (user == null) {
            logger.warn("未授权的工单关闭请求");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            Ticket ticket = ticketService.closeTicket(id, user);
            return ResponseEntity.ok(convertToDTO(ticket));
        } catch (IllegalStateException e) {
            logger.warn("关闭工单失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (TicketNotFoundException e) {
            logger.warn("工单未找到: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("工单未找到");
        }
    }

    /**
     * 重新打开工单
     */
    @PostMapping("/reopen/{id}")
    public ResponseEntity<?> reopenTicket(@PathVariable Long id,
                                          HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        logger.info("收到重新打开工单请求，ID: {}", id);

        User user = authenticationService.getAuthenticatedUser(httpRequest, httpResponse);
        if (user == null) {
            logger.warn("未授权的工单重开请求");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            Ticket ticket = ticketService.reopenTicket(id, user);
            return ResponseEntity.ok(convertToDTO(ticket));
        } catch (IllegalStateException e) {
            logger.warn("重新打开工单失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (TicketNotFoundException e) {
            logger.warn("工单未找到: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("工单未找到");
        }
    }

    /**
     * 回复工单
     */
    @PostMapping("/reply")
    public ResponseEntity<?> replyTicket(@RequestBody TicketDTO ticketDto,
                                         HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        logger.info("收到工单回复请求，工单ID: {}", ticketDto.getId());

        User user = authenticationService.getAuthenticatedUser(httpRequest, httpResponse);
        if (user == null) {
            logger.warn("未授权的工单回复请求");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            Chat chat = ticketService.replyTicket(ticketDto, user);
            return ResponseEntity.ok(chat);
        } catch (IllegalArgumentException e) {
            logger.warn("工单回复参数验证失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            logger.warn("工单回复失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (TicketNotFoundException e) {
            logger.warn("工单未找到: {}", ticketDto.getId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("工单未找到");
        }
    }

    /**
     * 获取用户工单列表
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getTicketsByUserId(@PathVariable Long userId,
                                                HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        logger.info("收到获取用户工单列表请求，用户ID: {}", userId);

        User user = authenticationService.getAuthenticatedUser(httpRequest, httpResponse);
        if (user == null) {
            logger.warn("未授权的工单列表访问请求");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!user.getId().equals(userId) && !userService.isAdminUser(user)) {
            logger.warn("无权访问其他用户的工单列表，请求用户: {}，目标用户: {}", user.getId(), userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("没有权限查看该用户的工单");
        }

        List<Ticket> tickets = ticketService.getUserTickets(userId);
        return ResponseEntity.ok(tickets.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList()));
    }

    /**
     * 获取所有工单列表
     */
    @GetMapping("/all")
    public ResponseEntity<?> getAllTickets(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        logger.info("收到获取所有工单列表请求");

        User user = authenticationService.getAuthenticatedUser(httpRequest, httpResponse);
        if (user == null) {
            logger.warn("未授权的所有工单访问请求");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!userService.isAdminUser(user)) {
            logger.warn("非管理员用户尝试访问所有工单列表，用户ID: {}", user.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("没有权限查看所有工单");
        }

        List<Ticket> tickets = ticketService.getAllTickets();
        return ResponseEntity.ok(tickets.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList()));
    }

    /**
     * 获取用户工单列表（分页）
     */
    @GetMapping("/user/{userId}/paged")
    public ResponseEntity<?> getTicketsByUserIdPaged(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "true") boolean open,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        logger.info("收到获取用户工单列表（分页）请求，用户ID: {}, 状态: {}, 页码: {}, 每页大小: {}",
                userId, open ? "开启" : "关闭", page, size);

        User user = authenticationService.getAuthenticatedUser(httpRequest, httpResponse);
        if (user == null) {
            logger.warn("未授权的工单列表访问请求");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!user.getId().equals(userId) && !userService.isAdminUser(user)) {
            logger.warn("无权访问其他用户的工单列表，请求用户: {}，目标用户: {}", user.getId(), userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("没有权限查看该用户的工单");
        }

        Page<Ticket> ticketsPage = ticketService.getUserTicketsPaged(userId, open, page, size);
        Page<TicketDTO> dtoPage = ticketsPage.map(this::convertToDTO);

        return ResponseEntity.ok(dtoPage);
    }

    /**
     * 获取所有工单列表（分页）
     */
    @GetMapping("/all/paged")
    public ResponseEntity<?> getAllTicketsPaged(
            @RequestParam(required = false) Boolean open,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        logger.info("收到获取所有工单列表（分页）请求，状态: {}, 页码: {}, 每页大小: {}",
                open != null ? (open ? "开启" : "关闭") : "全部", page, size);

        User user = authenticationService.getAuthenticatedUser(httpRequest, httpResponse);
        if (user == null) {
            logger.warn("未授权的所有工单访问请求");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!userService.isAdminUser(user)) {
            logger.warn("非管理员用户尝试访问所有工单列表，用户ID: {}", user.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("没有权限查看所有工单");
        }

        Page<Ticket> ticketsPage;
        if (open != null) {
            ticketsPage = ticketService.getAllTicketsPaged(open, page, size);
        } else {
            ticketsPage = ticketService.getAllTicketsPaged(page, size);
        }

        Page<TicketDTO> dtoPage = ticketsPage.map(this::convertToDTO);

        return ResponseEntity.ok(dtoPage);
    }

    /**
     * 获取当前管理员的已分配工单列表（分页）
     */
    @GetMapping("/admin/assigned/paged")
    public ResponseEntity<?> getCurrentAdminAssignedTicketsPaged(
            @RequestParam(defaultValue = "true") boolean open,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        logger.info("收到获取当前管理员已分配工单列表（分页）请求，状态: {}, 页码: {}, 每页大小: {}",
                open ? "开启" : "关闭", page, size);

        User user = authenticationService.getAuthenticatedUser(httpRequest, httpResponse);
        if (user == null) {
            logger.warn("未授权的请求");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!userService.isAdminUser(user)) {
            logger.warn("非管理员用户请求管理员工单列表，用户ID: {}", user.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("没有权限获取管理员工单列表");
        }

        try {
            Page<Ticket> ticketsPage = ticketService.getAdminAssignedTicketsPaged(user, open, page, size);
            Page<TicketDTO> dtoPage = ticketsPage.map(this::convertToDTO);

            return ResponseEntity.ok(dtoPage);
        } catch (Exception e) {
            logger.error("获取管理员工单列表时发生错误", e);
            return ResponseEntity.internalServerError().body("获取管理员工单列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定管理员的已分配工单列表（分页）
     */
    @GetMapping("/admin/{adminId}/assigned/paged")
    public ResponseEntity<?> getAdminAssignedTicketsPaged(
            @PathVariable Long adminId,
            @RequestParam(defaultValue = "true") boolean open,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        logger.info("收到获取指定管理员已分配工单列表（分页）请求，管理员ID: {}, 状态: {}, 页码: {}, 每页大小: {}",
                adminId, open ? "开启" : "关闭", page, size);

        User user = authenticationService.getAuthenticatedUser(httpRequest, httpResponse);
        if (user == null) {
            logger.warn("未授权的请求");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!userService.isAdminUser(user)) {
            logger.warn("非管理员用户请求指定管理员工单列表，用户ID: {}", user.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("没有权限获取管理员工单列表");
        }

        try {
            // 验证目标管理员是否存在且为管理员
            User targetAdmin = userService.getUserById(adminId);
            if (targetAdmin == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("指定的管理员不存在");
            }

            if (!userService.isAdminUser(targetAdmin)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("指定的用户不是管理员");
            }

            Page<Ticket> ticketsPage = ticketService.getAdminAssignedTicketsPaged(adminId, open, page, size);
            Page<TicketDTO> dtoPage = ticketsPage.map(this::convertToDTO);

            return ResponseEntity.ok(dtoPage);
        } catch (Exception e) {
            logger.error("获取指定管理员工单列表时发生错误", e);
            return ResponseEntity.internalServerError().body("获取管理员工单列表失败: " + e.getMessage());
        }
    }

    /**
     * 转移工单
     */
    @PostMapping("/transfer/{ticketId}/{newAdminId}")
    public ResponseEntity<?> transferTicket(@PathVariable Long ticketId, @PathVariable Long newAdminId,
                                            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        logger.info("收到转移工单请求，工单ID: {}，新管理员ID: {}", ticketId, newAdminId);

        User user = authenticationService.getAuthenticatedUser(httpRequest, httpResponse);
        if (user == null || !userService.isAdminUser(user)) {
            logger.warn("未授权的工单转移请求");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            Ticket ticket = ticketService.getTicket(ticketId);
            User newAdmin = userService.getUserById(newAdminId);
            if (!userService.isAdminUser(newAdmin)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("目标用户不是管理员");
            }
            ticket.setAssignedAdmin(newAdmin);
            ticketService.save(ticket);
            return ResponseEntity.ok("工单转移成功");
        } catch (TicketNotFoundException e) {
            logger.warn("工单未找到: {}", ticketId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("工单未找到");
        } catch (Exception e) {
            logger.error("转移工单时发生错误", e);
            return ResponseEntity.internalServerError().body("转移工单失败: " + e.getMessage());
        }
    }

    /**
     * 将Ticket实体转换为DTO
     */
    private TicketDTO convertToDTO(Ticket ticket) {
        logger.debug("正在将工单转换为DTO。工单ID: {}", ticket.getId());
        TicketDTO dto = new TicketDTO();
        dto.setId(ticket.getId());
        dto.setIssue(ticket.getIssue());
        dto.setType(ticket.getType());
        dto.setCreatedDate(ticket.getCreatedDate());
        dto.setClosedDate(ticket.getClosedDate());
        dto.setOpen(ticket.isOpen());
        dto.setUserId(ticket.getUser().getId());
        dto.setClosedByAdmin(ticket.isClosedByAdmin());
        dto.setAssignedAdminId(ticket.getAssignedAdmin() != null ? ticket.getAssignedAdmin().getId() : null);

        // 安全地处理聊天记录集合
        Set<ChatDTO> chatDTOs = new HashSet<>();
        if (ticket.getChats() != null && !ticket.getChats().isEmpty()) {
            chatDTOs = ticket.getChats().stream()
                    .filter(Objects::nonNull)  // 过滤掉可能的空记录
                    .map(chat -> {
                        ChatDTO chatDTO = new ChatDTO();
                        chatDTO.setId(chat.getId());
                        chatDTO.setMessage(chat.getMessage());
                        chatDTO.setTimestamp(chat.getTimestamp());

                        // 安全地设置用户信息
                        if (chat.getUser() != null) {
                            UserDTO userDTO = getUserDTO(chat);
                            chatDTO.setUser(userDTO);
                        }

                        // 设置最小化的工单信息以避免循环引用
                        TicketDTO minTicketDTO = new TicketDTO();
                        minTicketDTO.setId(ticket.getId());
                        minTicketDTO.setIssue(ticket.getIssue());
                        minTicketDTO.setType(ticket.getType());
                        minTicketDTO.setOpen(ticket.isOpen());
                        chatDTO.setTicket(minTicketDTO);

                        return chatDTO;
                    })
                    .collect(Collectors.toSet());
        }
        dto.setChats(chatDTOs);

        if (ticket.getClosedBy() != null) {
            dto.setClosedByUserId(ticket.getClosedBy().getId());
        }

        logger.debug("工单转换为DTO完成。工单ID: {}", ticket.getId());
        return dto;
    }

    @NotNull
    private static UserDTO getUserDTO(Chat chat) {
        UserDTO userDTO = new UserDTO();
        userDTO.setId(chat.getUser().getId());
        userDTO.setUsername(chat.getUser().getUsername());
        userDTO.setEmail(chat.getUser().getEmail());
        userDTO.setRegistrationDate(chat.getUser().getRegistrationDate());
        userDTO.setBirthday(chat.getUser().getBirthday());
        userDTO.setGender(chat.getUser().getGender());
        userDTO.setUserVerificationStatus(chat.getUser().getUserVerificationStatus());
        userDTO.setUserGroup(chat.getUser().getUserGroup());
        return userDTO;
    }
}