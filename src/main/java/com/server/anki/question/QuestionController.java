package com.server.anki.question;

import com.server.anki.auth.AuthenticationService;
import com.server.anki.question.dto.QuestionDTO;
import com.server.anki.question.dto.QuestionReplyDTO;
import com.server.anki.question.entity.Question;
import com.server.anki.question.entity.QuestionReply;
import com.server.anki.question.enums.QuestionStatus;
import com.server.anki.question.enums.QuestionType;
import com.server.anki.question.exception.NotFoundException;
import com.server.anki.question.exception.UnauthorizedException;
import com.server.anki.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {
    private static final Logger logger = LoggerFactory.getLogger(QuestionController.class);

    @Autowired
    private QuestionService questionService;

    @Autowired
    private AuthenticationService authenticationService;

    @PostMapping
    public ResponseEntity<?> createQuestion(
            @RequestParam QuestionType questionType,
            @RequestParam String description,
            @RequestParam String contactInfo,
            @RequestParam String contactName,
            @RequestParam(required = false) MultipartFile image,  // 新增图片参数
            @RequestParam String shortTitle,  // 添加shortTitle参数
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            User user = authenticationService.getAuthenticatedUser(httpRequest, httpResponse);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
            }

            Question question = questionService.createQuestion(questionType, description, contactInfo, contactName, user, image, shortTitle);
            return ResponseEntity.ok(questionService.getQuestion(question.getId(), user));
        } catch (RuntimeException e) {
            logger.error("创建问题失败", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> getQuestions(
            @RequestParam(required = false) QuestionStatus status, // 修改status参数为非必需
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            User user = authenticationService.getAuthenticatedUser(httpRequest, httpResponse);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
            }

            PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<QuestionDTO> questions;

            if (status == null) {  // 当status为null时查询所有状态
                questions = questionService.getAllQuestions(pageRequest);
            } else {
                questions = questionService.getQuestions(status, pageRequest);
            }

            return ResponseEntity.ok(questions);
        } catch (Exception e) {
            logger.error("获取问题列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/hot")
    public ResponseEntity<?> getHotQuestions(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            User user = authenticationService.getAuthenticatedUser(httpRequest, httpResponse);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
            }

            List<QuestionDTO> questions = questionService.getHotQuestions();
            return ResponseEntity.ok(questions);
        } catch (Exception e) {
            logger.error("获取热门问题失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchQuestions(
            @RequestParam String keyword,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            User user = authenticationService.getAuthenticatedUser(httpRequest, httpResponse);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
            }

            List<QuestionDTO> questions = questionService.searchQuestions(keyword);
            return ResponseEntity.ok(questions);
        } catch (Exception e) {
            logger.error("搜索问题失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserQuestions(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            User user = authenticationService.getAuthenticatedUser(httpRequest, httpResponse);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
            }

            PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<QuestionDTO> questions = questionService.getUserQuestions(userId, pageRequest);
            return ResponseEntity.ok(questions);
        } catch (Exception e) {
            logger.error("获取用户问题列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/user/{userId}/solved")
    public ResponseEntity<?> getUserSolvedQuestions(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            User user = authenticationService.getAuthenticatedUser(httpRequest, httpResponse);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
            }

            PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<QuestionDTO> questions = questionService.getUserSolvedQuestions(userId, pageRequest);
            return ResponseEntity.ok(questions);
        } catch (Exception e) {
            logger.error("获取用户解决的问题列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/{questionId}")
    public ResponseEntity<?> getQuestion(
            @PathVariable Long questionId,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            User user = authenticationService.getAuthenticatedUser(httpRequest, httpResponse);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
            }

            QuestionDTO question = questionService.getQuestion(questionId, user);
            return ResponseEntity.ok(question);
        } catch (RuntimeException e) {
            logger.error("获取问题详情失败", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{questionId}/replies")
    public ResponseEntity<?> getQuestionReplies(
            @PathVariable Long questionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            User user = authenticationService.getAuthenticatedUser(httpRequest, httpResponse);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
            }

            PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<QuestionReplyDTO> replies = questionService.getQuestionReplies(questionId, pageRequest);
            return ResponseEntity.ok(replies);
        } catch (Exception e) {
            logger.error("获取问题回复失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PostMapping("/{questionId}/replies")
    public ResponseEntity<?> replyToQuestion(
            @PathVariable Long questionId,
            @RequestBody Map<String, String> request,  // 修改为使用 Map 接收请求体
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            User user = authenticationService.getAuthenticatedUser(httpRequest, httpResponse);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
            }

            String content = request.get("content");  // 从请求体中获取 content
            if (content == null || content.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("回复内容不能为空");
            }

            QuestionReply reply = questionService.replyToQuestion(questionId, content, user);
            // 验证回复是否成功创建
            if (reply.getId() == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("回复创建失败");
            }
            return ResponseEntity.ok(questionService.getQuestion(questionId, user));
        } catch (RuntimeException e) {
            logger.error("回复问题失败", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{questionId}/replies/{replyId}/apply")
    public ResponseEntity<?> applyToSolve(
            @PathVariable Long questionId,
            @PathVariable Long replyId,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            User user = authenticationService.getAuthenticatedUser(httpRequest, httpResponse);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
            }

            questionService.applyToSolve(questionId, replyId, user);
            return ResponseEntity.ok(questionService.getQuestion(questionId, user));
        } catch (RuntimeException e) {
            logger.error("申请解决问题失败", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{questionId}/replies/{replyId}/accept")
    public ResponseEntity<?> acceptApplication(
            @PathVariable Long questionId,
            @PathVariable Long replyId,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            User user = authenticationService.getAuthenticatedUser(httpRequest, httpResponse);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
            }

            questionService.acceptApplication(questionId, replyId, user);
            return ResponseEntity.ok(questionService.getQuestion(questionId, user));
        } catch (RuntimeException e) {
            logger.error("接受申请失败", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{questionId}/resolve")
    public ResponseEntity<?> markAsResolved(
            @PathVariable Long questionId,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            User user = authenticationService.getAuthenticatedUser(httpRequest, httpResponse);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
            }

            questionService.markAsResolved(questionId, user);
            return ResponseEntity.ok(questionService.getQuestion(questionId, user));
        } catch (RuntimeException e) {
            logger.error("标记问题已解决失败", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{questionId}/close")
    public ResponseEntity<?> closeQuestion(
            @PathVariable Long questionId,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            User user = authenticationService.getAuthenticatedUser(httpRequest, httpResponse);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
            }

            questionService.closeQuestion(questionId, user);
            return ResponseEntity.ok(questionService.getQuestion(questionId, user));
        } catch (RuntimeException e) {
            logger.error("关闭问题失败", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{questionId}/replies/{replyId}/reject")
    public ResponseEntity<?> rejectSolution(
            @PathVariable Long questionId,
            @PathVariable Long replyId,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            User user = authenticationService.getAuthenticatedUser(httpRequest, httpResponse);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
            }

            questionService.rejectSolution(questionId, replyId, user);
            return ResponseEntity.ok(questionService.getQuestion(questionId, user));
        } catch (RuntimeException e) {
            logger.error("拒绝解决方案失败", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{questionId}")
    public ResponseEntity<?> deleteQuestion(
            @PathVariable Long questionId,
            HttpServletRequest request,
            HttpServletResponse response) {
        logger.info("收到删除问题请求, 问题ID: {}", questionId);

        try {
            User user = authenticationService.getAuthenticatedUser(request, response);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
            }

            questionService.deleteQuestion(questionId, user);
            return ResponseEntity.ok("问题删除成功");

        } catch (UnauthorizedException e) {
            logger.warn("删除问题权限不足: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (NotFoundException e) {
            logger.warn("删除问题未找到: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            logger.error("删除问题时发生错误", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("删除问题失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{questionId}/replies/{replyId}")
    public ResponseEntity<?> deleteReply(
            @PathVariable Long questionId,
            @PathVariable Long replyId,
            HttpServletRequest request,
            HttpServletResponse response) {
        logger.info("收到删除回复请求, 问题ID: {}, 回复ID: {}", questionId, replyId);

        try {
            User user = authenticationService.getAuthenticatedUser(request, response);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
            }

            questionService.deleteReply(questionId, replyId, user);
            return ResponseEntity.ok("回复删除成功");

        } catch (UnauthorizedException e) {
            logger.warn("删除回复权限不足: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (NotFoundException e) {
            logger.warn("删除回复未找到: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.warn("删除回复参数错误: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("删除回复时发生错误", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("删除回复失败: " + e.getMessage());
        }
    }
}