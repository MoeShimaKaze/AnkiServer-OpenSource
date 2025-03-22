package com.server.anki.rating;

import com.server.anki.auth.AuthenticationService;
import com.server.anki.user.User;
import com.server.anki.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ratings")
public class RatingController {
    private static final Logger logger = LoggerFactory.getLogger(RatingController.class);

    @Autowired
    private RatingService ratingService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserService userService;

    /**
     * 创建评价
     */
    @PostMapping
    public ResponseEntity<?> createRating(@RequestBody RatingDTO ratingDTO,
                                          HttpServletRequest request,
                                          HttpServletResponse response) {
        logger.info("收到创建评价请求: 订单: {}, 类型: {}",
                ratingDTO.getOrderNumber(), ratingDTO.getRatingType());

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        try {
            Rating rating = ratingService.createRating(
                    user.getId(),
                    ratingDTO.getOrderNumber(),
                    ratingDTO.getComment(),
                    ratingDTO.getScore(),
                    ratingDTO.getRatingType(),
                    ratingDTO.getOrderType()
            );

            return ResponseEntity.ok(convertToDTO(rating));
        } catch (IllegalStateException | IllegalArgumentException e) {
            logger.error("创建评价失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 获取用户发出的评价
     */
    @GetMapping("/from/{userId}")
    public ResponseEntity<?> getUserSentRatings(@PathVariable Long userId,
                                                Pageable pageable,
                                                HttpServletRequest request,
                                                HttpServletResponse response) {
        logger.info("收到获取用户发出评价请求: 用户: {}", userId);

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        // 只有自己或管理员可以查看用户发出的评价
        if (!user.getId().equals(userId) && !userService.isAdminUser(user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("无权查看其他用户发出的评价");
        }

        try {
            Page<Rating> ratings = ratingService.getRaterRatings(userId, pageable);
            Page<RatingDTO> ratingDTOs = ratings.map(this::convertToDTO);
            return ResponseEntity.ok(ratingDTOs);
        } catch (Exception e) {
            logger.error("获取用户发出的评价失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 获取订单评价
     */
    @GetMapping("/order/{orderNumber}")
    public ResponseEntity<?> getOrderRatings(@PathVariable String orderNumber,
                                             @RequestParam(required = false) OrderType orderType,
                                             HttpServletRequest request,
                                             HttpServletResponse response) {
        logger.info("收到获取订单评价请求: 订单: {}, 类型: {}", orderNumber, orderType);

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        try {
            UUID orderUUID = UUID.fromString(orderNumber);
            List<Rating> ratings;

            if (orderType == null) {
                // 自动判断订单类型
                ratings = ratingService.getOrderRatings(orderUUID);
            } else {
                ratings = ratingService.getOrderRatings(orderUUID, orderType);
            }

            List<RatingDTO> ratingDTOs = ratings.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(ratingDTOs);
        } catch (IllegalArgumentException e) {
            logger.error("获取订单评价失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 获取订单评价 - 兼容前端直接路径请求
     * 添加这个接口以匹配前端 /api/ratings/{orderNumber} 的请求
     */
    @GetMapping("/{orderNumber}")
    public ResponseEntity<?> getOrderRatingsDirect(@PathVariable String orderNumber,
                                                   @RequestParam(required = false) OrderType orderType,
                                                   HttpServletRequest request,
                                                   HttpServletResponse response) {
        logger.info("收到直接路径获取订单评价请求: 订单: {}, 类型: {}", orderNumber, orderType);
        return getOrderRatings(orderNumber, orderType, request, response);
    }

    /**
     * 获取用户收到的评价
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserRatings(@PathVariable Long userId,
                                            Pageable pageable,
                                            HttpServletRequest request,
                                            HttpServletResponse response) {
        logger.info("收到获取用户评价请求: 用户: {}", userId);

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        // 只有自己或管理员可以查看用户评价
        if (!user.getId().equals(userId) && !userService.isAdminUser(user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("无权查看其他用户评价");
        }

        try {
            Page<Rating> ratings = ratingService.getRatedUserRatings(userId, pageable);
            Page<RatingDTO> ratingDTOs = ratings.map(this::convertToDTO);
            return ResponseEntity.ok(ratingDTOs);
        } catch (Exception e) {
            logger.error("获取用户评价失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 删除评价
     */
    @DeleteMapping("/{ratingId}")
    public ResponseEntity<?> deleteRating(@PathVariable Long ratingId,
                                          HttpServletRequest request,
                                          HttpServletResponse response) {
        logger.info("收到删除评价请求: ID: {}", ratingId);

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }

        // 只有管理员可以删除评价
        if (!userService.isAdminUser(user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("无权删除评价");
        }

        try {
            ratingService.deleteRating(ratingId);
            return ResponseEntity.ok("评价已删除");
        } catch (Exception e) {
            logger.error("删除评价失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body("删除评价失败: " + e.getMessage());
        }
    }

    /**
     * 将Rating实体转换为DTO
     */
    private RatingDTO convertToDTO(Rating rating) {
        RatingDTO dto = new RatingDTO();
        dto.setId(rating.getId());
        dto.setRaterId(rating.getRater().getId());
        dto.setRaterName(rating.getRater().getUsername());

        if (rating.getRatedUser() != null) {
            dto.setRatedUserId(rating.getRatedUser().getId());
            dto.setRatedUserName(rating.getRatedUser().getUsername());
        }

        dto.setComment(rating.getComment());
        dto.setScore(rating.getScore());
        dto.setRatingDate(rating.getRatingDate());
        dto.setRatingType(rating.getRatingType());
        dto.setRatingTypeDescription(rating.getRatingType().getDescription());
        dto.setOrderType(rating.getOrderType());
        dto.setOrderTypeDescription(rating.getOrderType().getDescription());
        dto.setOrderNumber(rating.getOrderNumberAsUUID());

        return dto;
    }
}