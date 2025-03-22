package com.server.anki.shopping.controller;

import com.server.anki.auth.AuthenticationService;
import com.server.anki.shopping.controller.response.StoreResponse;
import com.server.anki.shopping.dto.StoreDTO;
import com.server.anki.shopping.entity.Store;
import com.server.anki.shopping.enums.StoreStatus;
import com.server.anki.shopping.repository.StoreRepository;
import com.server.anki.shopping.service.StoreService;
import com.server.anki.user.User;
import com.server.anki.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/stores")
@Tag(name = "店铺管理", description = "店铺创建、信息管理相关接口")
public class StoreController {
    private static final Logger logger = LoggerFactory.getLogger(StoreController.class);

    @Autowired
    private StoreService storeService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserService userService;

    @Autowired
    private StoreRepository storeRepository;

    @PostMapping
    @Operation(summary = "创建店铺")
    public ResponseEntity<StoreResponse> createStore(@Valid @RequestBody StoreDTO storeDTO) {
        logger.info("收到店铺创建请求，商家ID: {}, 店铺名称: {}, 店铺地址: {}",
                storeDTO.getMerchantId(), storeDTO.getStoreName(), storeDTO.getLocation());
        logger.debug("店铺创建请求详细信息: 联系电话: {}, 营业时间: {}, 经纬度: [{}, {}]",
                storeDTO.getContactPhone(), storeDTO.getBusinessHours(),
                storeDTO.getLatitude(), storeDTO.getLongitude());

        try {
            Store store = storeService.createStore(storeDTO);
            logger.info("店铺创建成功，店铺ID: {}, 店铺名称: {}, 店铺状态: {}",
                    store.getId(), store.getStoreName(), store.getStatus());
            return ResponseEntity.ok(StoreResponse.fromStore(store));
        } catch (Exception e) {
            logger.error("店铺创建失败，商家ID: {}, 店铺名称: {}, 错误类型: {}, 错误信息: {}",
                    storeDTO.getMerchantId(), storeDTO.getStoreName(),
                    e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }

    @PutMapping("/{storeId}")
    @Operation(summary = "更新店铺信息")
    public ResponseEntity<StoreResponse> updateStore(
            @PathVariable Long storeId,
            @Valid @RequestBody StoreDTO storeDTO) {
        logger.info("收到店铺信息更新请求，店铺ID: {}, 店铺名称: {}, 商家ID: {}",
                storeId, storeDTO.getStoreName(), storeDTO.getMerchantId());
        logger.debug("店铺更新请求详细信息: 店铺描述: {}, 联系电话: {}, 地址: {}",
                storeDTO.getDescription(), storeDTO.getContactPhone(), storeDTO.getLocation());

        try {
            Store store = storeService.updateStore(storeId, storeDTO);
            logger.info("店铺信息更新成功，店铺ID: {}, 店铺名称: {}, 更新时间: {}",
                    store.getId(), store.getStoreName(), store.getUpdatedAt());
            return ResponseEntity.ok(StoreResponse.fromStore(store));
        } catch (Exception e) {
            logger.error("店铺信息更新失败，店铺ID: {}, 错误类型: {}, 错误信息: {}",
                    storeId, e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }

    @GetMapping("/pending-review")
    @Operation(summary = "获取待审核的店铺列表", description = "获取所有状态为待审核的店铺")
    public ResponseEntity<?> getPendingReviewStores(
            Pageable pageable,
            HttpServletRequest request,
            HttpServletResponse response) {

        User currentUser = authenticationService.getAuthenticatedUser(request, response);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 验证是否为管理员
        if (!userService.isAdminUser(currentUser)) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "只有管理员才能访问待审核店铺列表");

            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(errorResponse);
        }

        try {
            Page<Store> stores = storeRepository.findStoreByStatus(StoreStatus.PENDING_REVIEW, pageable);
            Page<StoreResponse> storeResponses = stores.map(StoreResponse::fromStore);

            logger.info("获取待审核店铺成功，总数: {}", stores.getTotalElements());

            return ResponseEntity.ok(storeResponses);
        } catch (Exception e) {
            logger.error("获取待审核店铺失败: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "获取待审核店铺列表失败: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }
    }

    /**
     * 审核店铺申请
     */
    @PostMapping("/{storeId}/audit")
    @Operation(summary = "审核店铺申请", description = "管理员审核店铺申请，通过或拒绝")
    public ResponseEntity<?> auditStore(
            @PathVariable Long storeId,
            @RequestParam boolean approved,
            @RequestParam(required = false) String remarks,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到店铺审核请求，店铺ID: {}, 是否通过: {}", storeId, approved);

        User currentUser = authenticationService.getAuthenticatedUser(request, response);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 验证是否为管理员
        if (!userService.isAdminUser(currentUser)) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "只有管理员才能审核店铺");

            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(errorResponse);
        }

        try {
            Store store = storeService.auditStore(storeId, approved, remarks);

            // 构建成功响应
            Map<String, Object> successResponse = new HashMap<>();
            successResponse.put("success", true);
            successResponse.put("message", approved ? "店铺审核已通过" : "店铺审核已拒绝");
            successResponse.put("storeId", store.getId());
            successResponse.put("storeName", store.getStoreName());
            successResponse.put("status", store.getStatus());

            return ResponseEntity.ok(successResponse);
        } catch (Exception e) {
            logger.error("店铺审核失败，店铺ID: {}, 错误类型: {}, 错误信息: {}",
                    storeId, e.getClass().getSimpleName(), e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "店铺审核失败: " + e.getMessage());

            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PutMapping("/{storeId}/status")
    @Operation(summary = "更新店铺状态")
    public ResponseEntity<Void> updateStoreStatus(
            @PathVariable Long storeId,
            @RequestParam StoreStatus status) {
        logger.info("收到更新店铺状态请求，店铺ID: {}, 目标状态: {}", storeId, status);

        try {
            storeService.updateStoreStatus(storeId, status);
            logger.info("店铺状态更新成功，店铺ID: {}, 新状态: {}", storeId, status);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("店铺状态更新失败，店铺ID: {}, 目标状态: {}, 错误类型: {}, 错误信息: {}",
                    storeId, status, e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }

    @GetMapping("/nearby")
    @Operation(summary = "查找附近的店铺")
    public ResponseEntity<List<StoreResponse>> findNearbyStores(
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam Double distance) {
        logger.info("收到查找附近店铺请求，位置: [{}, {}], 距离范围: {}公里", latitude, longitude, distance);

        try {
            List<Store> stores = storeService.findNearbyStores(latitude, longitude, distance);
            List<StoreResponse> responses = stores.stream()
                    .map(StoreResponse::fromStore)
                    .collect(Collectors.toList());

            logger.info("查找附近店铺成功，位置: [{}, {}], 距离: {}公里, 找到店铺数量: {}",
                    latitude, longitude, distance, responses.size());

            if (!responses.isEmpty()) {
                logger.debug("附近店铺ID列表: {}",
                        responses.stream()
                                .map(r -> r.getId() + ":" + r.getStoreName())
                                .collect(Collectors.joining(", ")));
            } else {
                logger.debug("指定范围内未找到店铺");
            }

            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            logger.error("查找附近店铺失败，位置: [{}, {}], 距离: {}公里, 错误类型: {}, 错误信息: {}",
                    latitude, longitude, distance, e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }

    @GetMapping("/search")
    @Operation(summary = "搜索店铺")
    public ResponseEntity<Page<StoreResponse>> searchStores(
            @RequestParam String keyword,
            Pageable pageable) {
        logger.info("收到搜索店铺请求，关键词: {}, 分页参数: 页码={}, 每页大小={}, 排序={}",
                keyword, pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort());

        try {
            Page<Store> stores = storeService.searchStores(keyword, pageable);
            Page<StoreResponse> responses = stores.map(StoreResponse::fromStore);

            logger.info("搜索店铺成功，关键词: {}, 当前页店铺数量: {}, 总记录数: {}, 总页数: {}",
                    keyword, responses.getNumberOfElements(),
                    responses.getTotalElements(), responses.getTotalPages());

            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            logger.error("搜索店铺失败，关键词: {}, 错误类型: {}, 错误信息: {}",
                    keyword, e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }
    
    @GetMapping("/merchant/{merchantId}")
    @Operation(summary = "获取商家的店铺列表")
    public ResponseEntity<List<StoreResponse>> getMerchantStores(@PathVariable Long merchantId) {
        logger.info("收到获取商家店铺列表请求，商家ID: {}", merchantId);

        try {
            List<Store> stores = storeService.getMerchantStores(merchantId);
            List<StoreResponse> responses = stores.stream()
                    .map(StoreResponse::fromStore)
                    .collect(Collectors.toList());

            logger.info("获取商家店铺列表成功，商家ID: {}, 店铺数量: {}", merchantId, responses.size());

            if (!responses.isEmpty()) {
                String storeDetails = responses.stream()
                        .map(r -> String.format("ID:%d,名称:%s,状态:%s",
                                r.getId(), r.getStoreName(), r.getStatus()))
                        .collect(Collectors.joining(" | "));
                logger.debug("商家店铺详情: {}", storeDetails);
            } else {
                logger.debug("该商家没有店铺");
            }

            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            logger.error("获取商家店铺列表失败，商家ID: {}, 错误类型: {}, 错误信息: {}",
                    merchantId, e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }

    @GetMapping("/merchant/current")
    @Operation(summary = "获取当前用户的店铺列表")
    public ResponseEntity<?> getCurrentUserStores(
            HttpServletRequest request,
            HttpServletResponse response) {

        User currentUser = authenticationService.getAuthenticatedUser(request, response);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            // 获取用户的商店列表，即使用户不是商家也不会报错
            List<Store> stores = storeService.getMerchantStores(currentUser.getId());

            // 为店铺添加额外的统计信息
            // 这里可以添加额外的统计信息，如店铺的订单数、商品数等
            List<StoreResponse> storeResponses = stores.stream()
                    .map(StoreResponse::fromStore)
                    .collect(Collectors.toList());

            logger.info("成功获取当前用户的店铺列表, userId: {}, 店铺数量: {}",
                    currentUser.getId(), storeResponses.size());

            return ResponseEntity.ok(storeResponses);
        } catch (Exception e) {
            logger.error("获取当前用户店铺失败，返回空列表: {}", e.getMessage(), e);

            // 出现异常时返回空列表，而不是错误响应
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    @GetMapping("/all")
    @Operation(summary = "获取所有店铺列表(管理员)")
    public ResponseEntity<?> getAllStores(
            Pageable pageable,
            HttpServletRequest request,
            HttpServletResponse response) {

        User currentUser = authenticationService.getAuthenticatedUser(request, response);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 验证是否为管理员
        if (!userService.isAdminUser(currentUser)) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "只有管理员才能访问所有店铺列表");

            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(errorResponse);
        }

        try {
            Page<Store> stores = storeService.getActiveStores(pageable);
            Page<StoreResponse> storeResponses = stores.map(StoreResponse::fromStore);

            return ResponseEntity.ok(storeResponses);
        } catch (Exception e) {
            logger.error("获取所有店铺失败: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "获取店铺列表失败: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }
    }

    /**
     * 获取店铺列表
     */
    @GetMapping
    @Operation(summary = "获取店铺列表", description = "分页获取店铺列表，支持关键词搜索")
    public ResponseEntity<Page<StoreResponse>> getStores(
            @RequestParam(required = false) String keyword,
            Pageable pageable) {
        logger.info("收到获取店铺列表请求, 关键词: {}", keyword);

        try {
            Page<Store> stores;
            if (keyword != null && !keyword.trim().isEmpty()) {
                stores = storeService.searchStores(keyword, pageable);
            } else {
                // 获取活跃状态的店铺列表
                stores = storeService.getActiveStores(pageable);
            }

            Page<StoreResponse> responses = stores.map(StoreResponse::fromStore);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            logger.error("获取店铺列表失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{storeId}")
    @Operation(summary = "获取店铺详情")
    public ResponseEntity<?> getStoreDetail(
            @PathVariable Long storeId,
            HttpServletRequest request,
            HttpServletResponse response) {

        User currentUser = authenticationService.getAuthenticatedUser(request, response);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            Store store = storeService.getStoreById(storeId);

            // 验证权限 - 商家本人或管理员可以查看详情
            boolean isOwner = store.getMerchant().getId().equals(currentUser.getId());
            boolean isAdmin = userService.isAdminUser(currentUser);

            if (!isOwner && !isAdmin) {
                // 非商家本人和非管理员只能查看ACTIVE状态的店铺
                if (store.getStatus() != StoreStatus.ACTIVE) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("message", "该店铺不处于营业状态，无法查看详情");

                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(errorResponse);
                }
            }

            return ResponseEntity.ok(StoreResponse.fromStore(store));
        } catch (Exception e) {
            logger.error("获取店铺详情失败: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "获取店铺详情失败: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }
    }
}