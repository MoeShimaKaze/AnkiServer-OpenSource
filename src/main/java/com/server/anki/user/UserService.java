package com.server.anki.user;

import com.server.anki.email.EmailService;
import com.server.anki.register.RegistrationRequest;
import com.server.anki.storage.MinioService;
import com.server.anki.user.enums.UserIdentity;
import com.server.anki.user.enums.UserVerificationStatus;
import com.server.anki.wallet.message.WalletInitMessage;
import com.server.anki.wallet.message.WalletMessageUtils;
import com.server.anki.wallet.repository.WalletRepository;
import com.server.anki.wallet.service.WalletService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UserService {

    private static final String ADMIN_GROUP = "admin";
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // 需要添加的新依赖注入
    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private MinioService minioService;

    @Autowired
    private EmailService emailService;

    /**
     * 该方法使用 @PostConstruct 注解，表示在依赖注入完成后执行此方法以进行初始化。
     * 注解用于抑制在 @PostConstruct 方法中调用事务方法的相关警告。
     */
    @SuppressWarnings("SpringTransactionalMethodCallsInspection")
    @PostConstruct
    public void initSystemAccount() {
        try {
            getOrCreateSystemAccount();
            logger.info("系统账户检查完成");
        } catch (Exception e) {
            logger.error("初始化系统账户时发生错误", e);
        }
    }

    /**
     * 获取或创建系统账户
     * 由于涉及钱包创建，需要改为发送消息队列处理
     */
    @Transactional
    public User getOrCreateSystemAccount() {
        logger.debug("正在尝试获取或创建系统账户");
        return userRepository.findBySystemAccount(true)
                .orElseGet(() -> {
                    logger.info("系统账户不存在，正在创建新账户");
                    User systemUser = new User();
                    systemUser.setUsername("SystemAccount");
                    systemUser.setEmail("Lab@Foreactos.fun");
                    systemUser.setEncryptedPassword(passwordEncoder.encode("Lwj17665614803*_"));
                    systemUser.setRegistrationDate(LocalDate.now());
                    systemUser.setUserGroup("system");
                    systemUser.setSystemAccount(true);
                    systemUser.setUserVerificationStatus(UserVerificationStatus.VERIFIED);
                    systemUser.setCanLogin(false);

                    User savedUser = userRepository.save(systemUser);

                    // 发送钱包初始化消息
                    WalletInitMessage initMessage = WalletMessageUtils.createInitMessage(
                            savedUser.getId(),
                            savedUser.getUsername(),
                            savedUser.getUserVerificationStatus().name()
                    );
                    walletService.sendWalletMessage(initMessage);

                    logger.info("系统账户创建成功，已发送钱包初始化消息");
                    return savedUser;
                });
    }

    public Page<User> getAllUsers(Pageable pageable) {
        logger.debug("正在检索所有用户，分页：页码 - {}, 每页大小 - {}", pageable.getPageNumber(), pageable.getPageSize());
        return userRepository.findAll(pageable);
    }

    /**
     * 分页获取所有管理员用户
     * @param page 页码（从0开始）
     * @param size 每页数量
     * @return 管理员用户分页结果
     */
    public Page<User> getAllAdminsPaged(int page, int size) {
        logger.info("分页获取管理员用户列表: 页码={}, 每页大小={}", page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "username"));
        return userRepository.findByUserGroup("admin", pageable);
    }

    public User getUserById(Long userId) {
        logger.debug("正在通过ID检索用户: {}", userId);
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    logger.warn("未找到ID为: {} 的用户", userId);
                    return new RuntimeException("用户未找到: " + userId);
                });
    }

    @Transactional
    public void updateUserProfile(UserDTO userDto, MultipartFile avatarFile) throws Exception {
        logger.info("正在更新用户ID: {} 的个人资料", userDto.getId());

        User user = userRepository.findById(userDto.getId()).orElseThrow(() -> {
            logger.warn("未找到需要更新的用户。ID: {}", userDto.getId());
            return new Exception("用户未找到");
        });

        try {
            // 检查是否需要验证邮箱
            boolean needsEmailVerification = userDto.getEmail() != null &&
                    !userDto.getEmail().isEmpty() &&
                    !userDto.getEmail().equals(user.getEmail());

            // 检查是否需要验证密码修改
            boolean needsPasswordVerification = userDto.getNewPassword() != null &&
                    !userDto.getNewPassword().isEmpty();

            // 如果需要验证，检查验证码
            if ((needsEmailVerification || needsPasswordVerification) && userDto.getVerificationCode() != null) {
                // 验证邮箱验证码
                boolean isVerified = emailService.isEmailVerified(
                        needsEmailVerification ? userDto.getEmail() : user.getEmail(),
                        userDto.getVerificationCode()
                );

                if (!isVerified) {
                    logger.warn("用户: {} 的邮箱验证失败", user.getId());
                    throw new Exception("邮箱验证失败，请重新验证");
                }
            } else if (needsEmailVerification || needsPasswordVerification) {
                // 如果需要验证但没有提供验证码
                logger.warn("用户: {} 需要验证码但未提供", user.getId());
                throw new Exception("修改敏感信息需要验证码");
            }

            // 更新用户信息
            updateUserInfo(user, userDto);

            // 处理头像上传
            if (avatarFile != null && !avatarFile.isEmpty()) {
                String oldAvatarUrl = user.getAvatarUrl();
                updateUserAvatar(user, avatarFile);

                // 只有当用户已有头像时，才尝试删除旧头像
                if (oldAvatarUrl != null && !oldAvatarUrl.isEmpty()) {
                    try {
                        minioService.deleteFile(oldAvatarUrl);
                        logger.info("用户ID: {} 的旧头像已删除", user.getId());
                    } catch (Exception e) {
                        logger.warn("删除用户ID: {} 的旧头像失败。错误: {}",
                                user.getId(), e.getMessage());
                    }
                }
            }

            userRepository.save(user);
            logger.info("用户ID: {} 的个人资料更新成功", userDto.getId());
        } catch (Exception e) {
            logger.error("更新用户ID: {} 的个人资料时出错。错误: {}",
                    userDto.getId(), e.getMessage());
            throw new Exception("更新用户资料失败: " + e.getMessage());
        }
    }

    private void updateUserInfo(User user, UserDTO userDto) throws Exception {
        // 更新用户名
        if (userDto.getUsername() != null && !userDto.getUsername().isEmpty()
                && !userDto.getUsername().equals(user.getUsername())) {

            boolean usernameExists = userRepository.existsByUsername(userDto.getUsername());
            if (usernameExists) {
                logger.warn("用户名已存在: {}", userDto.getUsername());
                throw new Exception("用户名已经存在");
            }
            user.setUsername(userDto.getUsername());
            logger.info("用户ID: {} 的用户名已更新", user.getId());
        }

        // 更新邮箱
        if (userDto.getEmail() != null && !userDto.getEmail().isEmpty()
                && !userDto.getEmail().equals(user.getEmail())) {

            user.setEmail(userDto.getEmail());
            logger.info("用户ID: {} 的邮箱已更新", user.getId());
        }

        // 更新密码
        if (userDto.getNewPassword() != null && !userDto.getNewPassword().isEmpty()) {
            String encodedNewPassword = passwordEncoder.encode(userDto.getNewPassword());
            user.setEncryptedPassword(encodedNewPassword);
            logger.info("用户ID: {} 的密码已更新", user.getId());
        }

        // 更新生日
        if (userDto.getBirthday() != null) {
            user.setBirthday(userDto.getBirthday());
            logger.info("用户ID: {} 的生日已更新", user.getId());
        }

        // 更新性别
        if (userDto.getGender() != null && !userDto.getGender().isEmpty()) {
            user.setGender(userDto.getGender());
            logger.info("用户ID: {} 的性别已更新", user.getId());
        }
    }

    private void updateUserAvatar(User user, MultipartFile avatarFile) throws Exception {
        String fileName = generateUniqueFileName(user.getId(), avatarFile.getOriginalFilename());
        String avatarUrl = minioService.uploadFile(avatarFile, "avatars", fileName, true);
        user.setAvatarUrl(avatarUrl);
        logger.info("用户ID: {} 的头像已更新", user.getId());
    }

    private String generateUniqueFileName(Long userId, String originalFilename) {
        String fileExtension = getFileExtension(originalFilename);
        return String.format("avatar_%d_%d%s", userId, System.currentTimeMillis(), fileExtension);
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf(".") == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }


    /**
     * 用户注册核心逻辑
     *
     * @param registrationRequest 注册请求
     * @return 注册结果信息
     */
    @Transactional
    public User registerUser(RegistrationRequest registrationRequest) {
        logger.info("正在注册新用户，邮箱: {}", registrationRequest.getEmail());

        // 首先验证邮箱验证码
        if (!emailService.isEmailVerified(registrationRequest.getEmail(),
                registrationRequest.getVerificationCode())) {
            logger.warn("邮箱验证失败: {}", registrationRequest.getEmail());
            throw new IllegalArgumentException("邮箱验证失败，请重新验证邮箱");
        }

        // 检查邮箱是否已注册
        if (userRepository.findByEmail(registrationRequest.getEmail()).isPresent()) {
            logger.warn("邮箱已被注册: {}", registrationRequest.getEmail());
            throw new IllegalArgumentException("邮箱已经被注册！");
        }

        // 检查用户名是否已存在
        if (userRepository.existsByUsername(registrationRequest.getUsername())) {
            logger.warn("用户名已存在: {}", registrationRequest.getUsername());
            throw new IllegalArgumentException("用户名已经存在！");
        }

        // 创建新用户
        User newUser = new User();
        newUser.setUsername(registrationRequest.getUsername());
        newUser.setEmail(registrationRequest.getEmail());
        newUser.setEncryptedPassword(passwordEncoder.encode(registrationRequest.getPassword()));
        newUser.setRegistrationDate(LocalDate.now());
        newUser.setBirthday(registrationRequest.getBirthday());
        newUser.setGender(registrationRequest.getGender());
        newUser.setUserVerificationStatus(UserVerificationStatus.UNVERIFIED); // 默认未验证
        newUser.setUserGroup("user"); // 默认分组

        User savedUser = userRepository.save(newUser);
        logger.info("用户注册成功: {}", newUser.getUsername());

        return savedUser;
    }

    @Transactional
    public User toggleUserLoginAbility(Long userId, boolean canLogin) {
        logger.info("切换用户登录能力。用户ID: {}, 是否可以登录: {}", userId, canLogin);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    logger.warn("切换登录能力时未找到用户。ID: {}", userId);
                    return new RuntimeException("用户未找到: " + userId);
                });

        if (user.getSystemAccount()) {
            logger.warn("尝试修改系统账户的登录能力。用户ID: {}", userId);
            throw new IllegalArgumentException("不能修改系统账户的登录能力");
        }

        user.setCanLogin(canLogin);
        User updatedUser = userRepository.save(user);
        logger.info("用户登录能力更新成功。用户ID: {}, 是否可以登录: {}", userId, canLogin);
        return updatedUser;
    }

    @Transactional
    public void submitVerification(Long userId, String idNumber, String realName, UserIdentity userIdentity,
                                   String studentId, String socialCreditCode,
                                   MultipartFile idCardFront, MultipartFile idCardBack,
                                   MultipartFile selfPhoto, MultipartFile additionalPhoto) throws Exception {
        logger.info("开始处理用户 {} 的实名认证申请", userId);

        // 获取用户并设置验证提交日期
        User user = getUserById(userId);

        // 设置验证提交日期
        user.setVerificationSubmissionDate(LocalDate.now());

        // 参数验证
        validateVerificationParams(idNumber, realName, userIdentity, studentId, socialCreditCode);
        validateVerificationFiles(userIdentity, idCardFront, idCardBack, selfPhoto, additionalPhoto);

        try {
            // 获取并更新个人信息
            Map<String, Object> personalInfo = user.getPersonalInfo() != null ?
                    user.getPersonalInfo() : new HashMap<>();

            // 更新实名信息
            personalInfo.put("id_number", idNumber);
            personalInfo.put("real_name", realName);

            // 根据用户身份更新信息
            if (userIdentity == UserIdentity.STUDENT) {
                personalInfo.put("student_id", studentId);
                user.setUserIdentity(UserIdentity.STUDENT);
            } else {
                // 对于非学生身份，设置相应的用户身份
                user.setUserIdentity(userIdentity);
            }

            // 将更新后的个人信息存储
            user.setPersonalInfo(personalInfo);

            // 上传认证文件
            try {
                // 如果存在旧的认证文件，先删除
                cleanupExistingVerificationFiles(user);

                // 上传新的认证文件，并将文件路径保存到 personal_info 中
                String idCardFrontUrl = minioService.uploadFile(
                        idCardFront,
                        "verification",
                        String.format("id_front_%d_%d", userId, System.currentTimeMillis()),
                        false
                );
                personalInfo.put("id_card_front_url", idCardFrontUrl);

                String idCardBackUrl = minioService.uploadFile(
                        idCardBack,
                        "verification",
                        String.format("id_back_%d_%d", userId, System.currentTimeMillis()),
                        false
                );
                personalInfo.put("id_card_back_url", idCardBackUrl);

                String selfPhotoUrl = minioService.uploadFile(
                        selfPhoto,
                        "verification",
                        String.format("self_%d_%d", userId, System.currentTimeMillis()),
                        false
                );
                personalInfo.put("self_photo_url", selfPhotoUrl);

                // 只有学生和平台专员需要上传附加照片
                if ((userIdentity == UserIdentity.STUDENT || userIdentity == UserIdentity.PLATFORM_STAFF)
                        && additionalPhoto != null && !additionalPhoto.isEmpty()) {
                    String additionalPhotoUrl = minioService.uploadFile(
                            additionalPhoto,
                            "verification",
                            String.format("additional_%d_%d", userId, System.currentTimeMillis()),
                            false
                    );
                    personalInfo.put("additional_photo_url", additionalPhotoUrl);
                }

                // 更新文件路径到 personal_info 后，保存用户信息
                user.setPersonalInfo(personalInfo);

            } catch (Exception e) {
                logger.error("用户 {} 认证文件上传失败", userId, e);
                cleanupExistingVerificationFiles(user);
                throw new Exception("认证文件上传失败：" + e.getMessage());
            }

            // 更新认证状态为待审核
            user.setUserVerificationStatus(UserVerificationStatus.PENDING);
            userRepository.save(user);

            logger.info("用户 {} 的实名认证申请已提交成功", userId);

        } catch (Exception e) {
            logger.error("处理用户 {} 的实名认证申请时发生错误", userId, e);
            throw e;
        }
    }

    @Transactional
    public void checkVerificationTimeout() {
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);

        // 获取所有未通过认证且提交过认证的用户
        List<User> users = userRepository.findAll().stream()
                .filter(user -> user.getUserVerificationStatus() == UserVerificationStatus.PENDING
                        && user.getVerificationSubmissionDate() != null
                        && user.getVerificationSubmissionDate().isBefore(thirtyDaysAgo))
                .toList();

        // 将这些用户的认证状态更新为 UNVERIFIED
        for (User user : users) {
            user.setUserVerificationStatus(UserVerificationStatus.UNVERIFIED);
            userRepository.save(user);
            logger.info("用户 {} 的认证状态已超时，已重置为 UNVERIFIED", user.getId());
        }
    }


    /**
     * 清理已存在的认证文件
     */
    private void cleanupExistingVerificationFiles(User user) {
        try {
            // 获取个人信息中的文件路径
            Map<String, Object> personalInfo = user.getPersonalInfo();

            // 删除已有的认证文件
            if (personalInfo.containsKey("id_card_front_url")) {
                String idCardFrontUrl = (String) personalInfo.get("id_card_front_url");
                if (idCardFrontUrl != null) {
                    minioService.deleteFile(idCardFrontUrl);
                    personalInfo.put("id_card_front_url", null); // 删除路径
                }
            }

            if (personalInfo.containsKey("id_card_back_url")) {
                String idCardBackUrl = (String) personalInfo.get("id_card_back_url");
                if (idCardBackUrl != null) {
                    minioService.deleteFile(idCardBackUrl);
                    personalInfo.put("id_card_back_url", null); // 删除路径
                }
            }

            if (personalInfo.containsKey("self_photo_url")) {
                String selfPhotoUrl = (String) personalInfo.get("self_photo_url");
                if (selfPhotoUrl != null) {
                    minioService.deleteFile(selfPhotoUrl);
                    personalInfo.put("self_photo_url", null); // 删除路径
                }
            }

            if (personalInfo.containsKey("additional_photo_url")) {
                String additionalPhotoUrl = (String) personalInfo.get("additional_photo_url");
                if (additionalPhotoUrl != null) {
                    minioService.deleteFile(additionalPhotoUrl);
                    personalInfo.put("additional_photo_url", null); // 删除路径
                }
            }

        } catch (Exception e) {
            logger.warn("清理用户旧认证文件时发生错误", e);
            // 继续执行，不中断主流程
        }
    }

    /**
     * 验证认证参数
     */
    private void validateVerificationParams(String idNumber, String realName, UserIdentity userIdentity,
                                            String studentId, String socialCreditCode) {
        if (!StringUtils.hasText(idNumber)) {
            throw new IllegalArgumentException("身份证号码不能为空");
        }
        if (!StringUtils.hasText(realName)) {
            throw new IllegalArgumentException("真实姓名不能为空");
        }
        if (userIdentity == null) {
            throw new IllegalArgumentException("请选择认证身份类型");
        }
        if (userIdentity == UserIdentity.STUDENT && !StringUtils.hasText(studentId)) {
            throw new IllegalArgumentException("学生认证需要提供学号");
        }
        if (userIdentity == UserIdentity.MERCHANT && !StringUtils.hasText(socialCreditCode)) {
            throw new IllegalArgumentException("商家认证需要提供统一社会信用代码");
        }
    }

    /**
     * 验证认证文件
     */
    private void validateVerificationFiles(UserIdentity userIdentity, MultipartFile idCardFront, MultipartFile idCardBack,
                                           MultipartFile selfPhoto, MultipartFile additionalPhoto) {
        if (idCardFront == null || idCardFront.isEmpty()) {
            throw new IllegalArgumentException("请上传身份证正面照片");
        }
        if (idCardBack == null || idCardBack.isEmpty()) {
            throw new IllegalArgumentException("请上传身份证背面照片");
        }
        if (selfPhoto == null || selfPhoto.isEmpty()) {
            throw new IllegalArgumentException("请上传自拍照片");
        }

        // 只有学生身份和平台专员身份才需要附加照片
        if ((userIdentity == UserIdentity.STUDENT || userIdentity == UserIdentity.PLATFORM_STAFF)
                && (additionalPhoto == null || additionalPhoto.isEmpty())) {
            String photoType = userIdentity == UserIdentity.STUDENT ? "学生证照片" : "合同照片";
            throw new IllegalArgumentException("请上传" + photoType);
        }
    }

    /**
     * 验证用户状态
     * 由于涉及钱包创建，需要改为发送消息队列处理
     */
    @Transactional
    public void verifyUser(Long userId, boolean approved) {
        User user = getUserById(userId);

        if (approved) {
            // 更新认证状态为已认证
            user.setUserVerificationStatus(UserVerificationStatus.VERIFIED);

            // 根据用户身份更新用户组
            UserIdentity identity = user.getUserIdentity();
            if (identity != null) {
                switch (identity) {
                    case PLATFORM_STAFF:
                        // 平台专员认证通过后更新为messenger用户组
                        user.setUserGroup("messenger");
                        logger.info("用户ID: {} 认证为平台专员，用户组已更新为messenger", user.getId());
                        break;
                    case STUDENT:
                        // 学生保持原有用户组
                        logger.info("用户ID: {} 认证为学生，用户组保持不变", user.getId());
                        break;
                    case VERIFIED_USER:
                        // 普通实名认证用户保持原有用户组
                        logger.info("用户ID: {} 认证为普通实名用户，用户组保持不变", user.getId());
                        break;
                    default:
                        logger.info("用户ID: {} 身份类型未识别，用户组保持不变", user.getId());
                        break;
                }
            }

            // 如果认证成功，发送钱包初始化消息
            if (walletRepository.findByUser(user).isEmpty()) {
                WalletInitMessage initMessage = WalletMessageUtils.createInitMessage(
                        user.getId(),
                        user.getUsername(),
                        UserVerificationStatus.VERIFIED.name()
                );
                walletService.sendWalletMessage(initMessage);
                logger.info("已为用户ID: {} 发送钱包初始化消息", user.getId());
            }
        } else {
            // 如果认证失败，更新状态为被拒绝
            user.setUserVerificationStatus(UserVerificationStatus.REJECTED);
        }

        userRepository.save(user);
        logger.info("用户ID: {} 的认证状态已更新。是否通过: {}", userId, approved);
    }



    /**
     * 判断用户是否为管理员
     * @param user 待判断的用户对象
     * @return 如果是管理员返回true，否则返回false
     */
    public boolean isAdminUser(User user) {
        if (user == null) {
            logger.warn("尝试判断null用户的管理员权限");
            return false;
        }

        String userGroup = user.getUserGroup();
        boolean isAdmin = ADMIN_GROUP.equalsIgnoreCase(userGroup);

        logger.debug("检查用户 {} 的管理员权限: {}", user.getId(), isAdmin);
        return isAdmin;
    }

    /**
     * 判断当前用户ID是否为管理员
     * @param userId 待判断的用户ID
     * @return 如果是管理员返回true，否则返回false
     */
    @SuppressWarnings("unused")
    public boolean isAdminUser(Long userId) {
        if (userId == null) {
            logger.warn("尝试判断null用户ID的管理员权限");
            return false;
        }

        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            logger.warn("未找到ID为 {} 的用户", userId);
            return false;
        }

        return isAdminUser(userOptional.get());
    }

    public List<User> getAllAdmins() {
        // 获取管理员列表调试日志
        logger.debug("正在获取所有管理员用户");
        return userRepository.findByUserGroup(ADMIN_GROUP);
    }

    public List<User> getUsersByIds(List<Long> userIds) {
        // 批量获取用户调试日志
        logger.debug("根据用户ID列表获取用户信息，用户ID列表：{}", userIds);
        return userRepository.findAllById(userIds);
    }

    public Page<User> getPendingVerificationUsers(int pageNumber, int pageSize) {
        logger.info("正在获取所有待实名认证审核的用户，分页：页码 - {}, 每页大小 - {}", pageNumber, pageSize);
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        return userRepository.findByUserVerificationStatus(UserVerificationStatus.PENDING, pageable);
    }

    public User saveUser(User user) {
        return userRepository.save(user);
    }
}