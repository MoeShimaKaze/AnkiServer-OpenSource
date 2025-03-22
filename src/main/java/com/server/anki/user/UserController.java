package com.server.anki.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.anki.auth.AuthenticationService;
import com.server.anki.user.enums.UserIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthenticationService authenticationService;

    @PutMapping("/update")
    public ResponseEntity<String> updateUserProfile(
            @RequestParam(value = "userDto", required = false) String userDtoJson,
            @RequestParam(value = "avatar", required = false) MultipartFile avatarFile,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到更新用户资料的请求");
        User authenticatedUser = authenticationService.getAuthenticatedUser(request, response);
        if (authenticatedUser == null) {
            logger.warn("未经授权的访问尝试更新用户资料");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            UserDTO userDto;
            if (userDtoJson != null && !userDtoJson.isEmpty()) {
                userDto = objectMapper.readValue(userDtoJson, UserDTO.class);
            } else {
                // 如果前端没传 userDto，就默认更新当前登录用户
                userDto = new UserDTO();
                userDto.setId(authenticatedUser.getId());
            }

            // 原先: if (!authenticatedUser.getId().equals(userDto.getId()) && isNotAdmin(authenticatedUser)) {...}
            if (!authenticatedUser.getId().equals(userDto.getId()) && !userService.isAdminUser(authenticatedUser)) {
                logger.warn("禁止访问尝试更新用户资料。用户ID: {}，目标ID: {}",
                        authenticatedUser.getId(), userDto.getId());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("没有权限更新此用户的资料");
            }

            userService.updateUserProfile(userDto, avatarFile);
            logger.info("用户资料更新成功。用户ID: {}", userDto.getId());
            return ResponseEntity.ok("个人资料更新成功！");
        } catch (Exception e) {
            logger.error("更新用户资料失败。错误: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("更新个人资料失败: " + e.getMessage());
        }
    }

    /**
     * 根据用户名查找用户
     */
    @GetMapping("/find-by-username")
    public ResponseEntity<?> findUserByUsername(@RequestParam String username,
                                                HttpServletRequest request,
                                                HttpServletResponse response) {
        logger.info("收到根据用户名查找用户请求: 用户名: {}", username);

        User authenticatedUser = authenticationService.getAuthenticatedUser(request, response);
        if (authenticatedUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        Optional<User> userOptional = userRepository.findByUsername(username);
        if (userOptional.isEmpty()) {
            logger.warn("未找到用户名为{}的用户", username);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("未找到该用户");
        }

        User user = userOptional.get();
        UserDTO userDto = convertToUserDTO(user);

        logger.info("根据用户名成功找到用户, ID: {}", user.getId());
        return ResponseEntity.ok(userDto);
    }

    @PostMapping("/avatars")
    public ResponseEntity<?> getAvatarsByUserIds(@RequestBody List<Long> userIds,
                                                 HttpServletRequest request,
                                                 HttpServletResponse response) {
        // 收到获取用户头像的请求
        logger.info("收到获取用户头像的请求，用户ID列表：{}", userIds);
        // 验证用户是否已登录
        User authenticatedUser = authenticationService.getAuthenticatedUser(request, response);
        if (authenticatedUser == null) {
            // 未授权访问警告
            logger.warn("未授权的获取头像请求尝试");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            // 从数据库中批量获取用户
            List<User> users = userService.getUsersByIds(userIds);

            // 生成 userId -> avatarUrl 的映射
            Map<Long, String> avatarMap = users.stream()
                    .collect(Collectors.toMap(User::getId, user ->
                            Optional.ofNullable(user.getAvatarUrl()).orElse("")
                    ));

            // 成功获取头像日志
            logger.info("成功获取用户头像，用户ID列表：{}", userIds);
            return ResponseEntity.ok(avatarMap);
        } catch (Exception e) {
            // 错误日志（包含异常堆栈）
            logger.error("获取用户头像时发生错误，用户ID列表：{}。错误信息：{}", userIds, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("获取头像失败: " + e.getMessage());
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getUserProfile(HttpServletRequest request, HttpServletResponse response) {
        // 收到获取用户资料请求
        logger.info("收到获取用户资料的请求");

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            // 未授权访问警告
            logger.warn("未授权的获取用户资料请求尝试");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UserDTO userDto = convertToUserDTO(user);
        // 成功获取资料日志
        logger.info("成功获取用户资料，用户ID：{}", user.getId());
        return ResponseEntity.ok(userDto);
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllUsers(HttpServletRequest request, HttpServletResponse response,
                                         @RequestParam int pageNumber, @RequestParam int pageSize) {
        logger.info("收到获取所有用户信息的请求，分页：页码 - {}, 每页大小 - {}", pageNumber, pageSize);
        User user = authenticationService.getAuthenticatedUser(request, response);

        // 保留原来的认证判断
        if (user == null) {
            logger.warn("未授权的访问尝试，无法获取所有用户信息");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 创建分页请求对象
        Pageable pageable = PageRequest.of(pageNumber, pageSize);

        // 调用服务层获取分页数据
        Page<User> userPage = userService.getAllUsers(pageable);

        // 将User对象转换为UserDTO对象，以避免序列化问题
        Page<UserDTO> userDtoPage = userPage.map(this::convertToUserDTO);

        // 返回分页数据（包含用户信息和分页信息）
        return ResponseEntity.ok(userDtoPage);
    }


    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        // 收到根据ID获取用户的请求
        logger.info("收到根据ID获取用户的请求，用户ID：{}", id);

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            // 未授权访问警告
            logger.warn("未授权的获取用户信息请求尝试，用户ID：{}", id);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<User> userOptional = userRepository.findById(id);
        if (userOptional.isEmpty()) {
            // 用户未找到警告
            logger.warn("用户未找到，用户ID：{}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("用户未找到");
        }

        UserDTO userDto = convertToUserDTO(userOptional.get());
        // 成功获取用户信息日志
        logger.info("成功获取用户信息，用户ID：{}", id);
        return ResponseEntity.ok(userDto);
    }

    @PutMapping("toggle-login/{userId}")
    public ResponseEntity<?> toggleUserLoginAbility(@PathVariable Long userId,
                                                    @RequestParam boolean canLogin,
                                                    HttpServletRequest request,
                                                    HttpServletResponse response) {
        // 收到修改用户登录权限请求
        logger.info("收到修改用户登录权限的请求，用户ID：{}，允许登录状态：{}", userId, canLogin);

        User adminUser = authenticationService.getAuthenticatedUser(request, response);
        if (adminUser == null) {
            // 未授权访问警告
            logger.warn("未授权的修改用户登录权限请求尝试，用户ID：{}", userId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未经授权的访问");
        }

        // 原先: if (!"admin".equals(adminUser.getUserGroup())) {...}
        if (!userService.isAdminUser(adminUser)) {
            // 权限不足警告（含操作者ID）
            logger.warn("无权限修改用户登录权限，目标用户ID：{}，操作者ID：{}",
                    userId, adminUser.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("无权限执行此操作");
        }

        try {
            User updatedUser = userService.toggleUserLoginAbility(userId, canLogin);
            UserDTO userDto = convertToUserDTO(updatedUser);
            // 成功修改登录权限日志
            logger.info("用户登录权限修改成功，用户ID：{}，新允许登录状态：{}", userId, canLogin);
            return ResponseEntity.ok(userDto);
        } catch (RuntimeException e) {
            // 修改失败错误日志（包含异常堆栈）
            logger.error("修改用户登录权限失败，用户ID：{}", userId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // 在UserController中修改的方法
    @PostMapping("/{userId}/verify")
    public ResponseEntity<?> submitVerification(HttpServletRequest request,
                                                HttpServletResponse response,
                                                @PathVariable Long userId,
                                                @RequestParam String idNumber,
                                                @RequestParam String realName,
                                                @RequestParam UserIdentity userIdentity,
                                                @RequestParam(required = false) String studentId,
                                                @RequestParam(required = false) String socialCreditCode,
                                                @RequestParam MultipartFile idCardFront,
                                                @RequestParam MultipartFile idCardBack,
                                                @RequestParam MultipartFile selfPhoto,
                                                @RequestParam MultipartFile additionalPhoto) {

        logger.info("收到用户实名认证提交请求，用户ID：{}", userId);

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null || !user.getId().equals(userId)) {
            logger.warn("未授权的实名认证提交尝试，用户ID：{}", userId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            // 如果是商家认证请求，直接重定向
            if (userIdentity == UserIdentity.MERCHANT) {
                logger.info("用户 {} 请求商家认证，重定向至商家认证流程", userId);

                // 创建商家认证重定向响应
                Map<String, Object> redirectInfo = new HashMap<>();
                redirectInfo.put("redirectToMerchantVerification", true);
                redirectInfo.put("userId", userId);
                redirectInfo.put("message", "请完成商家认证流程");
                redirectInfo.put("realName", realName);
                redirectInfo.put("idNumber", idNumber);

                return ResponseEntity
                        .status(HttpStatus.SEE_OTHER)
                        .body(redirectInfo);
            }

            // 非商家身份，执行正常的用户认证
            userService.submitVerification(userId, idNumber, realName, userIdentity,
                    studentId, socialCreditCode,
                    idCardFront, idCardBack, selfPhoto, additionalPhoto);

            return ResponseEntity.ok("实名认证信息提交成功，等待审核");
        } catch (Exception e) {
            // 处理用户认证过程中的商家认证重定向
            if (e.getMessage() != null && e.getMessage().contains("请完成商家认证流程")) {
                logger.info("用户 {} 认证过程中重定向至商家认证流程", userId);

                Map<String, Object> redirectInfo = new HashMap<>();
                redirectInfo.put("redirectToMerchantVerification", true);
                redirectInfo.put("userId", userId);
                redirectInfo.put("message", e.getMessage());
                redirectInfo.put("realName", realName);
                redirectInfo.put("idNumber", idNumber);

                return ResponseEntity
                        .status(HttpStatus.SEE_OTHER)
                        .body(redirectInfo);
            }

            logger.error("实名认证信息提交失败，用户ID：{}", userId, e);
            return ResponseEntity.badRequest().body("提交失败: " + e.getMessage());
        }
    }

    @PutMapping("/verify/{userId}")
    public ResponseEntity<?> verifyUser(@PathVariable Long userId,
                                        @RequestParam boolean approved,
                                        HttpServletRequest request,
                                        HttpServletResponse response) {

        // 收到实名认证审核请求
        logger.info("收到实名认证审核请求，用户ID：{}，审核结果：{}", userId, approved);

        User adminUser = authenticationService.getAuthenticatedUser(request, response);
        if (adminUser == null) {
            logger.warn("未授权的实名认证审核尝试，用户ID：{}", userId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未经授权的访问");
        }

        if (!userService.isAdminUser(adminUser)) {
            // 权限不足警告（含操作者信息）
            logger.warn("无权限执行实名认证审核，目标用户ID：{}，操作者ID：{}",
                    userId, adminUser.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("无权限执行此操作");
        }

        userService.verifyUser(userId, approved);
        logger.info("实名认证状态已更新，用户ID：{}，新状态：{}", userId, approved);
        return ResponseEntity.ok(approved ? "用户已通过实名认证" : "用户实名认证被拒绝");
    }

    @GetMapping("/pending-verification")
    public ResponseEntity<?> getPendingVerificationUsers(
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到获取待审核用户列表的请求，分页：页码 - {}, 每页大小 - {}", pageNumber, pageSize);

        User adminUser = authenticationService.getAuthenticatedUser(request, response);
        if (adminUser == null) {
            logger.warn("未授权的待审核用户列表访问尝试");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Collections.emptyList());
        }

        if (!userService.isAdminUser(adminUser)) {
            logger.warn("无权限查看待审核用户列表，操作者ID：{}", adminUser.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Collections.emptyList());
        }

        // 获取分页后的待审核用户
        Page<User> usersPage = userService.getPendingVerificationUsers(pageNumber, pageSize);

        // 创建包含分页信息的响应对象 - 改用不同的变量名
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("content", usersPage.getContent().stream()
                .map(this::convertToUserDTO)
                .collect(Collectors.toList()));
        responseData.put("totalElements", usersPage.getTotalElements());
        responseData.put("totalPages", usersPage.getTotalPages());
        responseData.put("number", usersPage.getNumber());
        responseData.put("size", usersPage.getSize());
        responseData.put("first", usersPage.isFirst());
        responseData.put("last", usersPage.isLast());
        responseData.put("empty", usersPage.isEmpty());

        logger.info("成功返回待审核用户列表，用户数量：{}", usersPage.getContent().size());
        return ResponseEntity.ok(responseData);
    }

    @GetMapping("/admins")
    public ResponseEntity<?> getAllAdmins(
            @RequestParam(defaultValue = "false") boolean paged,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request, HttpServletResponse response) {

        logger.info("收到获取管理员用户的请求，是否分页: {}", paged);

        User authenticatedUser = authenticationService.getAuthenticatedUser(request, response);
        if (authenticatedUser == null) {
            logger.warn("未授权的访问尝试获取管理员列表");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 检查调用者是否是管理员
        if (!userService.isAdminUser(authenticatedUser)) {
            logger.warn("非管理员用户尝试获取管理员列表，用户ID：{}", authenticatedUser.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("无权限执行此操作");
        }

        try {
            if (paged) {
                // 分页获取管理员列表
                Page<User> adminsPage = userService.getAllAdminsPaged(page, size);
                Page<UserDTO> adminDtosPage = adminsPage.map(this::convertToUserDTO);

                logger.info("成功获取管理员用户分页列表，第{}页，每页{}条，共{}条记录",
                        page, size, adminsPage.getTotalElements());
                return ResponseEntity.ok(adminDtosPage);
            } else {
                // 获取全部管理员列表（不分页）
                List<User> admins = userService.getAllAdmins();
                List<UserDTO> adminDtos = admins.stream()
                        .map(this::convertToUserDTO)
                        .collect(Collectors.toList());

                logger.info("成功获取全部管理员用户列表，共{}个管理员", admins.size());
                return ResponseEntity.ok(adminDtos);
            }
        } catch (Exception e) {
            logger.error("获取管理员列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("获取管理员列表失败: " + e.getMessage());
        }
    }

    /**
     * 将User实体转换为UserDTO，包含认证照片等个人信息
     * @param user 用户实体
     * @return 用户DTO
     */
    private UserDTO convertToUserDTO(User user) {
        return new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRegistrationDate(),
                user.getBirthday(),
                user.getGender(),
                user.getUserVerificationStatus(),
                user.getUserGroup(),
                user.getSystemAccount(),
                user.canLogin(),
                user.getAvatarUrl(),
                user.getUserIdentity(),
                user.getAlipayUserId(),
                user.getPersonalInfo()  // 添加personalInfo，包含认证照片URL
        );
    }
}
