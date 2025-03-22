package com.server.anki.question;

import com.server.anki.message.MessageType;
import com.server.anki.message.service.MessageService;
import com.server.anki.question.dto.QuestionDTO;
import com.server.anki.question.dto.QuestionReplyDTO;
import com.server.anki.question.entity.Question;
import com.server.anki.question.entity.QuestionReply;
import com.server.anki.question.enums.QuestionStatus;
import com.server.anki.question.enums.QuestionType;
import com.server.anki.question.exception.NotFoundException;
import com.server.anki.question.exception.ServiceException;
import com.server.anki.question.exception.UnauthorizedException;
import com.server.anki.question.repository.QuestionReplyRepository;
import com.server.anki.question.repository.QuestionRepository;
import com.server.anki.storage.MinioService;
import com.server.anki.user.User;
import com.server.anki.user.UserService;
import com.server.anki.websocket.NotificationWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class QuestionService {
    private static final Logger logger = LoggerFactory.getLogger(QuestionService.class);

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private QuestionReplyRepository replyRepository;

    @Autowired
    private MinioService minioService;

    @Autowired
    private UserService userService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private NotificationWebSocketHandler notificationWebSocketHandler;

    @Transactional
    public Question createQuestion(QuestionType questionType, String description,
                                   String contactInfo, String contactName, User user,
                                   MultipartFile imageFile, String shortTitle) {
        logger.info("Creating new question for user: {}", user.getId());

        // 参数验证
        if (questionType == null) {
            throw new RuntimeException("问题类型不能为空");
        }
        if (!StringUtils.hasText(description)) {
            throw new RuntimeException("问题描述不能为空");
        }
        if (!StringUtils.hasText(contactInfo)) {
            throw new RuntimeException("联系方式不能为空");
        }
        if (!StringUtils.hasText(contactName)) {
            throw new RuntimeException("联系人姓名不能为空");
        }
        if (!StringUtils.hasText(shortTitle)) {
            throw new RuntimeException("问题短标题不能为空");
        }
        if (shortTitle.length() > 30) {
            throw new RuntimeException("问题短标题不能超过30个字符");
        }

        Question question = new Question();
        question.setQuestionType(questionType);
        question.setDescription(Base64.getEncoder().encodeToString(description.getBytes()));
        question.setContactInfo(contactInfo);
        question.setContactName(contactName);
        question.setUser(user);
        question.setStatus(QuestionStatus.OPEN);
        question.setShortTitle(shortTitle);  // 设置短标题

        // 处理图片上传
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                String fileName = generateUniqueFileName(user.getId(), imageFile.getOriginalFilename());
                String imageUrl = minioService.uploadFile(
                        imageFile,
                        "question-images",
                        fileName,
                        true  // 永久存储
                );
                question.setImageUrl(imageUrl);
                logger.info("Question image uploaded successfully: {}", imageUrl);
            } catch (Exception e) {
                logger.error("Error uploading question image", e);
                throw new RuntimeException("图片上传失败: " + e.getMessage());
            }
        }

        return questionRepository.save(question);
    }

    // 生成唯一的文件名
    private String generateUniqueFileName(Long userId, String originalFilename) {
        String fileExtension = getFileExtension(originalFilename);
        return String.format("question_%d_%d%s", userId, System.currentTimeMillis(), fileExtension);
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf(".") == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }

    @Transactional
    public QuestionReply replyToQuestion(Long questionId, String content, User user) {
        logger.info("User {} attempting to reply to question {}", user.getId(), questionId);

        // 回复内容验证
        if (!StringUtils.hasText(content)) {
            throw new RuntimeException("回复内容不能为空");
        }
        if (content.length() > 1000) {
            throw new RuntimeException("回复内容不能超过1000字");
        }

        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("问题不存在"));

        if (question.getStatus() != QuestionStatus.OPEN) {
            throw new RuntimeException("该问题当前不接受回复");
        }

        if (questionRepository.hasUserReplied(questionId, user.getId())) {
            throw new RuntimeException("您已经回复过这个问题了");
        }

        QuestionReply reply = new QuestionReply();
        reply.setQuestion(question);
        reply.setUser(user);
        reply.setContent(content);

        QuestionReply savedReply = replyRepository.save(reply);

        // 发送站内信通知
        String messageContent = String.format("用户 %s 回复了您的问题", user.getUsername());
        messageService.sendMessage(question.getUser(), messageContent, MessageType.QUESTION_REPLIED, null);

        // 发送实时通知
        notificationWebSocketHandler.sendNotification(
                question.getUser().getId(),
                messageContent,
                MessageType.QUESTION_REPLIED.toString(),
                null
        );

        return savedReply;
    }

    @Transactional
    public void applyToSolve(Long questionId, Long replyId, User user) {
        logger.info("User {} applying to solve question {}", user.getId(), questionId);

        QuestionReply reply = replyRepository.findById(replyId)
                .orElseThrow(() -> new RuntimeException("回复不存在"));

        if (!reply.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("只能申请解决自己回复的问题");
        }

        if (reply.isApplied()) {
            throw new RuntimeException("您已经申请过了");
        }

        reply.apply();
        replyRepository.save(reply);

        // 发送站内信通知
        String messageContent = String.format("用户 %s 申请解决您的问题", user.getUsername());
        messageService.sendMessage(
                reply.getQuestion().getUser(),
                messageContent,
                MessageType.QUESTION_SOLUTION_APPLIED,
                null
        );

        // 发送实时通知
        notificationWebSocketHandler.sendNotification(
                reply.getQuestion().getUser().getId(),
                messageContent,
                MessageType.QUESTION_SOLUTION_APPLIED.toString(),
                null
        );
    }

    @Transactional
    public void acceptApplication(Long questionId, Long replyId, User user) {
        logger.info("User {} accepting application for question {}", user.getId(), questionId);

        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("问题不存在"));

        if (!question.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("只有问题发布者才能接受申请");
        }

        QuestionReply acceptedReply = replyRepository.findById(replyId)
                .orElseThrow(() -> new RuntimeException("回复不存在"));

        // 获取所有申请解决此问题的回复
        List<QuestionReply> appliedReplies = replyRepository.findByQuestionIdAndAppliedTrue(questionId);

        // 拒绝其他所有申请
        for (QuestionReply reply : appliedReplies) {
            if (!reply.getId().equals(replyId)) {
                reply.setRejected(true);
                replyRepository.save(reply);

                // 给被拒绝的申请者发送通知
                String notificationContent = String.format("您对问题 #%d 的解决方案申请未被采纳", questionId);
                messageService.sendMessage(
                        reply.getUser(),
                        notificationContent,
                        MessageType.QUESTION_SOLUTION_REJECTED,
                        null
                );

                notificationWebSocketHandler.sendNotification(
                        reply.getUser().getId(),
                        notificationContent,
                        MessageType.QUESTION_SOLUTION_REJECTED.toString(),
                        null
                );
            }
        }

        // 接受选中的方案
        question.acceptReply(acceptedReply);
        questionRepository.save(question);

        // 发送通知给被接受的申请者
        String messageContent = "您申请解决的问题已被采纳";
        messageService.sendMessage(
                acceptedReply.getUser(),
                messageContent,
                MessageType.QUESTION_SOLUTION_ACCEPTED,
                null
        );

        notificationWebSocketHandler.sendNotification(
                acceptedReply.getUser().getId(),
                messageContent,
                MessageType.QUESTION_SOLUTION_ACCEPTED.toString(),
                null
        );
    }

    @Transactional(readOnly = true)
    public Page<QuestionDTO> getQuestions(QuestionStatus status, Pageable pageable) {
        return questionRepository.findByStatus(status, pageable)
                .map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public List<QuestionDTO> getHotQuestions() {
        return questionRepository.findTop10ByOrderByViewCountDesc()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<QuestionDTO> searchQuestions(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            throw new RuntimeException("搜索关键词不能为空");
        }
        return questionRepository.searchQuestions(keyword)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<QuestionDTO> getUserQuestions(Long userId, Pageable pageable) {
        return questionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public Page<QuestionDTO> getUserSolvedQuestions(Long userId, Pageable pageable) {
        return questionRepository.findByAcceptedUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::convertToDTO);
    }

    @Transactional
    public QuestionDTO getQuestion(Long questionId, User currentUser) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("问题不存在"));

        // 增加查看次数
        question.incrementViewCount();
        questionRepository.save(question);

        QuestionDTO dto = convertToDTO(question);

        // 控制联系方式可见性
        if (!currentUser.getId().equals(question.getUser().getId()) &&
                (question.getAcceptedUser() == null ||
                        !currentUser.getId().equals(question.getAcceptedUser().getId()))) {
            dto.setContactInfo(null);
            dto.setContactName(null);
        }

        return dto;
    }

    @Transactional
    public void closeQuestion(Long questionId, User user) {
        logger.info("User {} attempting to close question {}", user.getId(), questionId);

        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("问题不存在"));

        if (!question.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("只有问题发布者才能关闭问题");
        }

        question.close();
        questionRepository.save(question);
    }

    @Transactional
    public void markAsResolved(Long questionId, User user) {
        logger.info("User {} marking question {} as resolved", user.getId(), questionId);

        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("问题不存在"));

        if (!question.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("只有问题发布者才能标记问题已解决");
        }

        question.markAsResolved();
        questionRepository.save(question);

        // 发送通知给解决者
        if (question.getAcceptedUser() != null) {
            String messageContent = "您解决的问题已被确认完成";
            messageService.sendMessage(
                    question.getAcceptedUser(),
                    messageContent,
                    MessageType.QUESTION_RESOLVED,
                    null
            );

            notificationWebSocketHandler.sendNotification(
                    question.getAcceptedUser().getId(),
                    messageContent,
                    MessageType.QUESTION_RESOLVED.toString(),
                    null
            );
        }
    }

    @Transactional
    public void rejectSolution(Long questionId, Long replyId, User user) {
        if (questionId == null || replyId == null || user == null) {
            logger.error("传入的参数存在空值: questionId={}, replyId={}, user={}", questionId, replyId, user);
            throw new IllegalArgumentException("必需的参数不能为空");
        }

        logger.info("正在处理拒绝解决方案请求，问题ID: {}, 回复ID: {}, 操作用户ID: {}",
                questionId, replyId, user.getId());

        // 获取问题信息并进行空值检查
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new NotFoundException("问题不存在: " + questionId));

        // 获取回复信息并进行空值检查
        QuestionReply reply = replyRepository.findById(replyId)
                .orElseThrow(() -> new NotFoundException("回复不存在: " + replyId));

        // 确保问题发布者不为空
        User questionOwner = question.getUser();
        if (questionOwner == null) {
            logger.error("问题 {} 的发布者信息缺失", questionId);
            throw new IllegalStateException("问题发布者信息缺失");
        }

        // 验证操作用户是否为问题发布者
        if (!questionOwner.getId().equals(user.getId())) {
            logger.warn("用户 {} 尝试拒绝问题 {} 的解决方案，但其不是问题发布者",
                    user.getId(), questionId);
            throw new UnauthorizedException("只有问题发布者才能拒绝解决方案");
        }

        // 获取回复者信息并进行空值检查
        User replier = reply.getUser();
        if (replier == null) {
            logger.error("回复 {} 的用户信息缺失", replyId);
            throw new IllegalStateException("回复者信息缺失");
        }

        // 验证回复状态
        if (!reply.isApplied()) {
            logger.warn("回复 {} 尚未申请解决问题", replyId);
            throw new IllegalStateException("该回复尚未申请解决问题");
        }

        if (reply.isRejected()) {
            logger.warn("回复 {} 已经被拒绝", replyId);
            throw new IllegalStateException("该回复已经被拒绝");
        }

        try {
            // 设置回复状态为已拒绝
            reply.setRejected(true);
            replyRepository.save(reply);

            // 创建通知消息
            String shortTitle = question.getShortTitle() != null ?
                    question.getShortTitle() : "未命名问题";
            String messageContent = String.format(
                    "您对问题「%s」(#%d) 的解决方案已被发布者拒绝",
                    shortTitle,
                    questionId
            );

            // 发送站内信通知
            messageService.sendMessage(
                    replier,                   // 消息接收者（被拒绝的回复者）
                    messageContent,            // 消息内容
                    MessageType.QUESTION_SOLUTION_REJECTED,  // 消息类型
                    questionId                 // 相关问题ID
            );

            // 发送实时通知
            notificationWebSocketHandler.sendNotification(
                    replier.getId(),           // 通知接收者ID
                    messageContent,            // 通知内容
                    MessageType.QUESTION_SOLUTION_REJECTED.toString(),  // 通知类型
                    questionId                 // 相关问题ID
            );

            logger.info("成功处理解决方案拒绝请求。问题ID: {}, 回复ID: {}, 拒绝者ID: {}",
                    questionId, replyId, user.getId());

        } catch (Exception e) {
            logger.error("处理解决方案拒绝请求时发生错误。问题ID: {}, 回复ID: {}",
                    questionId, replyId, e);
            throw new ServiceException("处理解决方案拒绝请求失败", e);
        }
    }

    @Transactional
    public void deleteQuestion(Long questionId, User user) {
        logger.info("正在删除问题, 问题ID: {}, 操作用户ID: {}", questionId, user.getId());

        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new NotFoundException("问题不存在: " + questionId));

        // 检查权限 - 只有管理员或问题创建者可以删除
        if (!userService.isAdminUser(user) && !question.getUser().getId().equals(user.getId())) {
            logger.warn("用户 {} 尝试删除非本人创建的问题 {}", user.getId(), questionId);
            throw new UnauthorizedException("只有管理员或问题创建者可以删除问题");
        }

        // 删除问题的所有回复
        replyRepository.deleteByQuestion(question);

        // 删除问题
        questionRepository.delete(question);

        // 发送删除通知
        if (!question.getUser().getId().equals(user.getId())) {
            messageService.sendMessage(
                    question.getUser(),
                    String.format("您的问题「%s」已被管理员删除", question.getShortTitle()),
                    MessageType.QUESTION_STATUS_UPDATED,
                    null
            );
        }

        logger.info("问题删除成功, 问题ID: {}", questionId);
    }

    @Transactional(readOnly = true)
    public Page<QuestionDTO> getAllQuestions(Pageable pageable) {
        return questionRepository.findAll(pageable)
                .map(this::convertToDTO);
    }

    @Transactional
    public void deleteReply(Long questionId, Long replyId, User user) {
        logger.info("正在删除回复, 问题ID: {}, 回复ID: {}, 操作用户ID: {}",
                questionId, replyId, user.getId());

        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new NotFoundException("问题不存在: " + questionId));

        QuestionReply reply = replyRepository.findById(replyId)
                .orElseThrow(() -> new NotFoundException("回复不存在: " + replyId));

        // 检查回复是否属于该问题
        if (!reply.getQuestion().getId().equals(questionId)) {
            logger.error("回复 {} 不属于问题 {}", replyId, questionId);
            throw new IllegalArgumentException("该回复不属于此问题");
        }

        // 检查权限 - 只有管理员或回复创建者可以删除
        if (!userService.isAdminUser(user) && !reply.getUser().getId().equals(user.getId())) {
            logger.warn("用户 {} 尝试删除非本人的回复 {}", user.getId(), replyId);
            throw new UnauthorizedException("只有管理员或回复创建者可以删除回复");
        }

        // 删除回复
        replyRepository.delete(reply);

        // 如果是管理员删除,发送通知给回复创建者
        if (userService.isAdminUser(user) && !reply.getUser().getId().equals(user.getId())) {
            messageService.sendMessage(
                    reply.getUser(),
                    String.format("您在问题「%s」下的回复已被管理员删除", question.getShortTitle()),
                    MessageType.QUESTION_STATUS_UPDATED,
                    null
            );
        }

        logger.info("回复删除成功, 回复ID: {}", replyId);
    }

    /**
     * 分页获取问题回复
     */
    @Transactional(readOnly = true)
    public Page<QuestionReplyDTO> getQuestionReplies(Long questionId, Pageable pageable) {
        // 先检查问题是否存在
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("问题不存在"));

        // 分页查询回复
        return replyRepository.findByQuestionIdOrderByCreatedAtDesc(questionId, pageable)
                .map(this::convertToReplyDTO);
    }

    private QuestionDTO convertToDTO(Question question) {
        QuestionDTO dto = new QuestionDTO();
        dto.setId(question.getId());
        dto.setQuestionType(question.getQuestionType());
        dto.setDescription(new String(Base64.getDecoder().decode(question.getDescription())));
        dto.setContactInfo(question.getContactInfo());
        dto.setContactName(question.getContactName());
        dto.setUserId(question.getUser().getId());
        dto.setUserName(question.getUser().getUsername());
        dto.setCreatedAt(question.getCreatedAt());
        dto.setStatus(question.getStatus());
        dto.setViewCount(question.getViewCount());
        dto.setResolvedAt(question.getResolvedAt());
        dto.setClosedAt(question.getClosedAt());
        dto.setImageUrl(question.getImageUrl());  // 添加图片URL转换
        dto.setShortTitle(question.getShortTitle());  // 添加短标题
        dto.setReplyCount(question.getReplies().size());  // 设置回复总数

        if (question.getAcceptedUser() != null) {
            dto.setAcceptedUserId(question.getAcceptedUser().getId());
            dto.setAcceptedUserName(question.getAcceptedUser().getUsername());
        }

        Set<QuestionReplyDTO> replyDTOs = question.getReplies().stream()
                .sorted(Comparator.comparing(QuestionReply::getCreatedAt).reversed())
                .limit(10)  // 只取最新的10条
                .map(this::convertToReplyDTO)
                .collect(Collectors.toSet());
        dto.setReplies(replyDTOs);

        return dto;
    }

    private QuestionReplyDTO convertToReplyDTO(QuestionReply reply) {
        QuestionReplyDTO dto = new QuestionReplyDTO();
        dto.setId(reply.getId());
        dto.setQuestionId(reply.getQuestion().getId());
        dto.setUserId(reply.getUser().getId());
        dto.setUserName(reply.getUser().getUsername());
        dto.setContent(reply.getContent());
        dto.setCreatedAt(reply.getCreatedAt());
        dto.setApplied(reply.isApplied());
        dto.setRejected(reply.isRejected());  // 添加这一行
        return dto;
    }
}