package com.server.anki.shopping.controller;

import com.server.anki.auth.AuthenticationService;
import com.server.anki.mailorder.enums.OrderStatus;
import com.server.anki.pay.payment.PaymentResponse;
import com.server.anki.rating.*;
import com.server.anki.shopping.controller.response.PurchaseRequestResponse;
import com.server.anki.shopping.dto.PurchaseRequestDTO;
import com.server.anki.shopping.entity.PurchaseRequest;
import com.server.anki.shopping.enums.DeliveryType;
import com.server.anki.shopping.enums.ProductCategory;
import com.server.anki.shopping.exception.InvalidOperationException;
import com.server.anki.shopping.service.PurchaseRequestService;
import com.server.anki.storage.MinioService;
import com.server.anki.user.User;
import com.server.anki.user.UserService;
import com.server.anki.utils.ApiResponse;
import com.server.anki.utils.exception.InvalidRequestException;
import com.server.anki.utils.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 代购需求控制器
 * 提供代购需求的发布、查询、状态更新等接口
 * 增强了错误处理能力，提供详细的错误响应
 */
@Slf4j
@RestController
@RequestMapping("/api/purchase-requests")
@Tag(name = "代购需求", description = "代购需求发布、查询、状态更新相关接口")
@Validated
public class PurchaseRequestController {
    private static final Logger logger = LoggerFactory.getLogger(PurchaseRequestController.class);

    @Autowired
    private PurchaseRequestService purchaseRequestService;

    @Autowired
    private UserService userService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private RatingService ratingService;

    @Autowired
    private MinioService minioService;

    /**
     * 发布代购需求
     *
     * @param userId 用户ID
     * @param title 标题
     * @param description 描述
     * @param category 分类
     * @param expectedPrice 预期价格
     * @param imageFile 图片文件
     * @param deadline 截止时间
     * @param purchaseAddress 代购地址
     * @param purchaseLatitude 代购地址纬度
     * @param purchaseLongitude 代购地址经度
     * @param deliveryAddress 配送地址
     * @param deliveryLatitude 配送地址纬度
     * @param deliveryLongitude 配送地址经度
     * @param recipientName 收件人姓名
     * @param recipientPhone 收件人电话
     * @param deliveryType 配送类型
     * @return 支付响应
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "发布代购需求")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功创建代购需求"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数有误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    public ResponseEntity<?> createRequest(
            @RequestParam("userId") @NotNull(message = "用户ID不能为空") Long userId,
            @RequestParam("title") @NotBlank(message = "标题不能为空") String title,
            @RequestParam("description") @NotBlank(message = "描述不能为空") String description,
            @RequestParam("category") @NotNull(message = "分类不能为空") ProductCategory category,
            @RequestParam("expectedPrice") @NotNull(message = "预期价格不能为空") @DecimalMin(value = "0.01", message = "价格必须大于0") BigDecimal expectedPrice,
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile,
            @RequestParam("deadline") @NotNull(message = "截止时间不能为空") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime deadline,
            @RequestParam("purchaseAddress") @NotBlank(message = "代购地址不能为空") String purchaseAddress,
            @RequestParam("purchaseLatitude") @NotNull(message = "代购地址纬度不能为空") Double purchaseLatitude,
            @RequestParam("purchaseLongitude") @NotNull(message = "代购地址经度不能为空") Double purchaseLongitude,
            @RequestParam("deliveryAddress") @NotBlank(message = "配送地址不能为空") String deliveryAddress,
            @RequestParam("deliveryLatitude") @NotNull(message = "配送地址纬度不能为空") Double deliveryLatitude,
            @RequestParam("deliveryLongitude") @NotNull(message = "配送地址经度不能为空") Double deliveryLongitude,
            @RequestParam("recipientName") @NotBlank(message = "收件人姓名不能为空") String recipientName,
            @RequestParam(value = "weight", required = false) Double weight,
            @RequestParam("recipientPhone") @NotBlank(message = "收件人电话不能为空") String recipientPhone,
            @RequestParam("deliveryType") @NotNull(message = "配送类型不能为空") DeliveryType deliveryType) {

        // 记录详细的请求参数
        logger.info("收到代购需求发布请求详情 - userId={}, title={}, category={}, expectedPrice={}, weight={}",
                userId, title, category, expectedPrice, weight);
        logger.info("代购地址: {}({}, {}), 配送地址: {}({}, {})",
                purchaseAddress, purchaseLatitude, purchaseLongitude,
                deliveryAddress, deliveryLatitude, deliveryLongitude);
        logger.info("收件人信息: {}, 电话: {}, 配送类型: {}, 截止时间: {}",
                recipientName, recipientPhone, deliveryType, deadline);
        logger.info("图片信息: {}", imageFile != null ?
                String.format("size=%d bytes, type=%s", imageFile.getSize(), imageFile.getContentType()) : "未上传");

        try {
            // 验证用户是否存在
            try {
                userService.getUserById(userId);
            } catch (Exception e) {
                logger.error("用户不存在: {}", userId);
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("用户不存在或已被禁用"));
            }

            // 验证图片是否上传
            if (imageFile == null || imageFile.isEmpty()) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("请上传商品参考图片"));
            }

            // 验证图片类型
            String contentType = imageFile.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("请上传图片格式的文件"));
            }

            // 验证图片大小
            if (imageFile.getSize() > 5 * 1024 * 1024) {
                return ResponseEntity
                        .status(HttpStatus.PAYLOAD_TOO_LARGE)
                        .body(ApiResponse.error("图片大小不能超过5MB"));
            }

            // 验证截止时间
            if (deadline.isBefore(LocalDateTime.now())) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("截止时间不能早于当前时间"));
            }

            // 验证坐标值是否在有效范围内
            if (purchaseLatitude < -90 || purchaseLatitude > 90 ||
                    purchaseLongitude < -180 || purchaseLongitude > 180 ||
                    deliveryLatitude < -90 || deliveryLatitude > 90 ||
                    deliveryLongitude < -180 || deliveryLongitude > 180) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("坐标值超出有效范围"));
            }

            // 构建PurchaseRequestDTO
            PurchaseRequestDTO requestDTO = new PurchaseRequestDTO();
            requestDTO.setUserId(userId);
            requestDTO.setTitle(title);
            requestDTO.setDescription(description);
            requestDTO.setCategory(category);
            requestDTO.setExpectedPrice(expectedPrice);
            requestDTO.setDeadline(deadline);
            requestDTO.setPurchaseAddress(purchaseAddress);
            requestDTO.setPurchaseLatitude(purchaseLatitude);
            requestDTO.setPurchaseLongitude(purchaseLongitude);
            requestDTO.setDeliveryAddress(deliveryAddress);
            requestDTO.setDeliveryLatitude(deliveryLatitude);
            requestDTO.setDeliveryLongitude(deliveryLongitude);
            requestDTO.setRecipientName(recipientName);
            requestDTO.setRecipientPhone(recipientPhone);
            requestDTO.setDeliveryType(deliveryType);
            requestDTO.setWeight(weight);

            // 处理图片上传
            try {
                String imageUrl = minioService.uploadFile(
                        imageFile,
                        "purchase",
                        String.format("purchase_%d_%d", userId, System.currentTimeMillis()),
                        true
                );
                requestDTO.setImageUrl(imageUrl);
                logger.info("代购需求图片上传成功: {}", imageUrl);
            } catch (Exception e) {
                logger.error("图片上传失败: {}", e.getMessage(), e);
                return ResponseEntity
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("图片上传失败: " + e.getMessage()));
            }

            // 创建代购需求
            PaymentResponse response = purchaseRequestService.createPurchaseRequest(requestDTO);

            // 详细记录支付响应内容
            logger.info("生成的支付响应: orderNumber={}, 到期时间={}, 金额={}",
                    response.getOrderNumber(), response.getExpireTime(), response.getAmount());

            // 记录payForm字段的前500个字符，避免日志过大
            String payFormPreview = response.getPayForm();
            if (payFormPreview != null && payFormPreview.length() > 2048) {
                payFormPreview = payFormPreview.substring(0, 2048) + "...";
            }
            logger.info("支付表单内容预览: {}", payFormPreview);

            // 检查payForm是否包含form标签
            boolean containsFormTag = response.getPayForm() != null &&
                    (response.getPayForm().contains("<form") || response.getPayForm().contains("</form>"));
            logger.info("支付表单是否包含form标签: {}", containsFormTag);

            return ResponseEntity.ok(response);

        } catch (InvalidOperationException e) {
            logger.error("无效操作: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("创建代购需求失败: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("创建代购需求失败: " + e.getMessage()));
        }
    }

    /**
     * 更新需求状态
     *
     * @param requestNumber 需求编号
     * @param status 新状态
     * @param assignedUserId 分配的用户ID（可选）
     * @return 响应
     */
    @PutMapping("/{requestNumber}/status")
    @Operation(summary = "更新需求状态")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功更新状态"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数有误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "需求不存在")
    })
    public ResponseEntity<?> updateRequestStatus(
            @PathVariable UUID requestNumber,
            @RequestParam OrderStatus status,
            @RequestParam(required = false) Long assignedUserId) {

        logger.info("收到需求状态更新请求，需求编号: {}, 新状态: {}", requestNumber, status);

        try {
            // 获取分配的配送员（如果有）
            User assignedUser = null;
            if (assignedUserId != null) {
                try {
                    assignedUser = userService.getUserById(assignedUserId);
                } catch (Exception e) {
                    return ResponseEntity
                            .status(HttpStatus.BAD_REQUEST)
                            .body(ApiResponse.error("指定的配送员不存在"));
                }
            }

            purchaseRequestService.updateRequestStatus(requestNumber, status, assignedUser);
            return ResponseEntity.ok(ApiResponse.success("状态更新成功"));

        } catch (InvalidOperationException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("需求不存在"));
        } catch (Exception e) {
            logger.error("更新需求状态失败: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("更新需求状态失败: " + e.getMessage()));
        }
    }

    /**
     * 增加需求浏览量
     *
     * @param requestNumber 需求编号
     * @return 成功消息
     */
    @PutMapping("/{requestNumber}/view")
    @Operation(summary = "增加需求浏览量")
    public ResponseEntity<?> incrementViewCount(@PathVariable UUID requestNumber) {
        logger.info("增加需求浏览量，需求编号: {}", requestNumber);

        try {
            boolean updated = purchaseRequestService.incrementViewCount(requestNumber);
            if (updated) {
                return ResponseEntity.ok(ApiResponse.success("浏览量已更新"));
            } else {
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("需求不存在"));
            }
        } catch (Exception e) {
            logger.error("增加浏览量失败: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("增加浏览量失败: " + e.getMessage()));
        }
    }

    /**
     * 获取推荐的代购需求列表
     *
     * @param userLatitude 用户纬度
     * @param userLongitude 用户经度
     * @param status 需求状态（可选）
     * @param pageable 分页参数
     * @return 分页的推荐需求列表
     */
    @GetMapping("/recommended")
    @Operation(summary = "获取推荐的代购需求列表")
    public ResponseEntity<?> getRecommendedRequests(
            @RequestParam Double userLatitude,
            @RequestParam Double userLongitude,
            @RequestParam(required = false) OrderStatus status,
            Pageable pageable) {

        logger.info("获取推荐需求列表，用户位置: {}, {}", userLatitude, userLongitude);

        try {
            // 验证坐标是否有效
            if (userLatitude < -90 || userLatitude > 90 || userLongitude < -180 || userLongitude > 180) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("无效的地理坐标"));
            }

            Page<PurchaseRequest> requests = purchaseRequestService.getRecommendedRequests(
                    userLatitude, userLongitude, status, pageable);

            return ResponseEntity.ok(
                    ApiResponse.success(requests.map(PurchaseRequestResponse::fromRequest),
                            Map.of(
                                    "totalPages", requests.getTotalPages(),
                                    "totalElements", requests.getTotalElements(),
                                    "currentPage", requests.getNumber()
                            ))
            );
        } catch (IllegalArgumentException e) {
            logger.error("获取推荐需求列表参数错误: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("获取推荐需求列表失败: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取推荐需求列表失败: " + e.getMessage()));
        }
    }

    /**
     * 获取所有可接单的互助配送代购需求
     *
     * @param pageable 分页参数
     * @return 分页的可接单互助配送代购需求列表
     */
    @GetMapping("/available-mutual")
    @Operation(summary = "获取可接单的互助配送代购需求列表")
    public ResponseEntity<?> getAvailableMutualRequests(Pageable pageable) {
        logger.info("获取可接单互助配送代购需求列表");

        try {
            Page<PurchaseRequest> requests = purchaseRequestService.getAvailableMutualRequests(pageable);
            return ResponseEntity.ok(
                    ApiResponse.success(requests.map(PurchaseRequestResponse::fromRequest),
                            Map.of(
                                    "totalPages", requests.getTotalPages(),
                                    "totalElements", requests.getTotalElements(),
                                    "currentPage", requests.getNumber()
                            ))
            );
        } catch (Exception e) {
            logger.error("获取可接单互助配送代购需求列表失败: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取可接单互助配送代购需求列表失败: " + e.getMessage()));
        }
    }

    /**
     * 搜索代购需求
     *
     * @param keyword 关键词
     * @param status 状态
     * @param pageable 分页参数
     * @return 分页结果
     */
    @GetMapping("/search")
    @Operation(summary = "搜索代购需求")
    public ResponseEntity<?> searchRequests(
            @RequestParam String keyword,
            @RequestParam(required = false) OrderStatus status,
            Pageable pageable) {

        try {
            Page<PurchaseRequest> requests = purchaseRequestService.searchRequests(keyword, status, pageable);
            return ResponseEntity.ok(
                    ApiResponse.success(requests.map(PurchaseRequestResponse::fromRequest),
                            Map.of(
                                    "totalPages", requests.getTotalPages(),
                                    "totalElements", requests.getTotalElements(),
                                    "currentPage", requests.getNumber()
                            ))
            );
        } catch (Exception e) {
            logger.error("搜索代购需求失败: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("搜索代购需求失败: " + e.getMessage()));
        }
    }

    /**
     * 获取用户发布的代购需求
     *
     * @param userId 用户ID
     * @param pageable 分页参数
     * @return 分页结果
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "获取用户发布的代购需求")
    public ResponseEntity<?> getUserRequests(
            @PathVariable Long userId,
            Pageable pageable) {

        try {
            User user = userService.getUserById(userId);
            Page<PurchaseRequest> requests = purchaseRequestService.getUserRequests(user, pageable);
            return ResponseEntity.ok(
                    ApiResponse.success(requests.map(PurchaseRequestResponse::fromRequest),
                            Map.of(
                                    "totalPages", requests.getTotalPages(),
                                    "totalElements", requests.getTotalElements(),
                                    "currentPage", requests.getNumber()
                            ))
            );
        } catch (ResourceNotFoundException e) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("用户不存在"));
        } catch (Exception e) {
            logger.error("获取用户代购需求失败: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取用户代购需求失败: " + e.getMessage()));
        }
    }

    /**
     * 获取代购需求详情
     *
     * @param requestNumber 需求编号
     * @return 代购需求详情
     */
    @GetMapping("/{requestNumber}")
    @Operation(summary = "获取代购需求详情")
    public ResponseEntity<?> getRequestDetail(@PathVariable UUID requestNumber) {
        logger.info("获取代购需求详情，需求编号: {}", requestNumber);

        try {
            Optional<PurchaseRequest> requestOpt = purchaseRequestService.findByRequestNumber(requestNumber);
            return requestOpt.map(purchaseRequest -> ResponseEntity.ok(
                    ApiResponse.success(PurchaseRequestResponse.fromRequest(purchaseRequest))
            )).orElseGet(() -> ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("代购需求不存在")));
        } catch (Exception e) {
            logger.error("获取代购需求详情失败: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("获取代购需求详情失败: " + e.getMessage()));
        }
    }

    /**
     * 评价代购需求
     *
     * @param requestNumber 需求编号
     * @param ratingDTO 评价数据
     * @param request HTTP请求
     * @param response HTTP响应
     * @return 评价结果
     */
    @PostMapping("/{requestNumber}/rate")
    @Operation(summary = "评价代购需求")
    public ResponseEntity<?> ratePurchaseRequest(
            @PathVariable UUID requestNumber,
            @RequestBody @Valid RatingDTO ratingDTO,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到评价商品代购订单请求: {}", requestNumber);

        try {
            User user = authenticationService.getAuthenticatedUser(request, response);
            if (user == null) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("用户未登录"));
            }

            // 设置默认的订单类型
            ratingDTO.setOrderType(OrderType.PURCHASE_REQUEST);
            ratingDTO.setOrderNumber(requestNumber);

            // 自动确定评价类型
            if (ratingDTO.getRatingType() == null) {
                Optional<PurchaseRequest> requestOptional = purchaseRequestService.findByRequestNumber(requestNumber);
                if (requestOptional.isPresent()) {
                    PurchaseRequest purchaseRequest = requestOptional.get();
                    if (purchaseRequest.getUser().getId().equals(user.getId())) {
                        // 需求方评价代购方
                        ratingDTO.setRatingType(RatingType.REQUESTER_TO_FULFILLER);
                    } else if (purchaseRequest.getAssignedUser() != null &&
                            purchaseRequest.getAssignedUser().getId().equals(user.getId())) {
                        // 代购方评价需求方
                        ratingDTO.setRatingType(RatingType.FULFILLER_TO_REQUESTER);
                    } else {
                        return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.error("无法确定评价类型，您不是该订单的需求方或配送方"));
                    }
                } else {
                    return ResponseEntity
                            .status(HttpStatus.NOT_FOUND)
                            .body(ApiResponse.error("需求不存在"));
                }
            }

            // 创建评价
            Rating rating = ratingService.createRating(
                    user.getId(),
                    requestNumber,
                    ratingDTO.getComment(),
                    ratingDTO.getScore(),
                    ratingDTO.getRatingType(),
                    OrderType.PURCHASE_REQUEST
            );

            return ResponseEntity.ok(ApiResponse.success(rating));

        } catch (InvalidRequestException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("创建评价失败: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("创建评价失败: " + e.getMessage()));
        }
    }
}