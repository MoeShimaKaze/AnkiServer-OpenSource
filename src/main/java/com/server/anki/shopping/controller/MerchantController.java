package com.server.anki.shopping.controller;

import com.server.anki.auth.AuthenticationService;
import com.server.anki.shopping.dto.MerchantDTO;
import com.server.anki.shopping.dto.MerchantEmployeeDTO;
import com.server.anki.shopping.dto.MerchantResponseDTO;
import com.server.anki.shopping.entity.MerchantInfo;
import com.server.anki.shopping.entity.MerchantUserMapping;
import com.server.anki.shopping.enums.MerchantUserRole;
import com.server.anki.shopping.exception.MerchantNotFoundException;
import com.server.anki.shopping.service.MerchantService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商家管理控制器
 * 提供商家入驻、信息更新、员工管理等接口
 */
@Slf4j
@RestController
@RequestMapping("/api/merchants")
@Tag(name = "商家管理", description = "商家入驻、信息管理、员工管理相关接口")
public class MerchantController {
    private static final Logger logger = LoggerFactory.getLogger(MerchantController.class);

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserService userService;

    @PostMapping
    @Operation(summary = "商家入驻申请")
    public ResponseEntity<MerchantInfo> registerMerchant(
            @Valid @RequestBody MerchantDTO merchantDTO,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到商家入驻申请，用户ID: {}", merchantDTO.getUserId());

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null || !user.getId().equals(merchantDTO.getUserId())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        MerchantInfo merchant = merchantService.registerMerchant(merchantDTO);
        return ResponseEntity.ok(merchant);
    }

    @PutMapping("/{merchantId}")
    @Operation(summary = "更新商家信息")
    public ResponseEntity<MerchantInfo> updateMerchant(
            @PathVariable Long merchantId,
            @Valid @RequestBody MerchantDTO merchantDTO,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到商家信息更新请求，商家ID: {}", merchantId);

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            MerchantInfo merchant = merchantService.getMerchantInfo(merchantId);

            // 检查权限
            if (!merchantService.isUserHasRequiredRole(user.getId(), merchant.getMerchantUid(), MerchantUserRole.ADMIN)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            MerchantInfo updatedMerchant = merchantService.updateMerchantInfo(merchantId, merchantDTO);
            return ResponseEntity.ok(updatedMerchant);
        } catch (MerchantNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{merchantId}")
    @Operation(summary = "获取商家信息")
    public ResponseEntity<MerchantInfo> getMerchant(
            @PathVariable Long merchantId,
            HttpServletRequest request,
            HttpServletResponse response) {

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            MerchantInfo merchant = merchantService.getMerchantInfo(merchantId);
            return ResponseEntity.ok(merchant);
        } catch (MerchantNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 根据UID获取商家信息
     */
    @GetMapping("/uid/{merchantUid}")
    @Operation(summary = "根据UID获取商家信息")
    public ResponseEntity<?> getMerchantByUid(
            @PathVariable String merchantUid,
            HttpServletRequest request,
            HttpServletResponse response) {

        User currentUser = authenticationService.getAuthenticatedUser(request, response);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            MerchantInfo merchant = merchantService.getMerchantInfoByUid(merchantUid);

            // 使用静态工厂方法
            MerchantResponseDTO merchantDTO = MerchantResponseDTO.fromEntity(merchant);

            return ResponseEntity.ok(merchantDTO);
        } catch (MerchantNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{merchantId}/evaluate")
    @Operation(summary = "评估商家等级")
    public ResponseEntity<Void> evaluateMerchantLevel(
            @PathVariable Long merchantId,
            HttpServletRequest request,
            HttpServletResponse response) {

        User user = authenticationService.getAuthenticatedUser(request, response);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            // 只有管理员可以手动触发等级评估
            if (!userService.isAdminUser(user)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            merchantService.evaluateMerchantLevel(merchantId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{merchantUid}/employees")
    @Operation(summary = "添加商家员工")
    public ResponseEntity<?> addEmployee(
            @PathVariable String merchantUid,
            @RequestParam Long userId,
            @RequestParam MerchantUserRole role,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到添加员工请求，merchantUid: {}, userId: {}, role: {}",
                merchantUid, userId, role);

        User currentUser = authenticationService.getAuthenticatedUser(request, response);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            // 验证权限
            if (!merchantService.isUserHasRequiredRole(currentUser.getId(), merchantUid, MerchantUserRole.ADMIN)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("您没有权限添加员工，需要管理员以上权限");
            }

            MerchantUserMapping mapping = merchantService.addEmployee(
                    merchantUid, userId, role, currentUser.getId());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("mappingId", mapping.getId());
            result.put("message", "员工添加成功");

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("添加员工失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("添加员工失败: " + e.getMessage());
        }
    }

    @GetMapping("/{merchantUid}/employees")
    @Operation(summary = "获取商家员工列表")
    public ResponseEntity<?> getEmployees(
            @PathVariable String merchantUid,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到获取员工列表请求，merchantUid: {}", merchantUid);

        User currentUser = authenticationService.getAuthenticatedUser(request, response);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            // 验证权限
            if (!merchantService.isUserHasRequiredRole(currentUser.getId(), merchantUid, MerchantUserRole.VIEWER)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("您没有权限查看员工列表");
            }

            List<MerchantEmployeeDTO> employees = merchantService.getMerchantEmployees(merchantUid);
            return ResponseEntity.ok(employees);
        } catch (Exception e) {
            logger.error("获取员工列表失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("获取员工列表失败: " + e.getMessage());
        }
    }

    @PutMapping("/{merchantUid}/employees/{userId}/role")
    @Operation(summary = "更新员工角色")
    public ResponseEntity<?> updateEmployeeRole(
            @PathVariable String merchantUid,
            @PathVariable Long userId,
            @RequestParam MerchantUserRole newRole,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到更新员工角色请求，merchantUid: {}, userId: {}, newRole: {}",
                merchantUid, userId, newRole);

        User currentUser = authenticationService.getAuthenticatedUser(request, response);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            // 验证权限
            if (!merchantService.isUserHasRequiredRole(currentUser.getId(), merchantUid, MerchantUserRole.OWNER)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("您没有权限更新员工角色，需要拥有者权限");
            }

            // 不能更新自己的角色
            if (currentUser.getId().equals(userId)) {
                return ResponseEntity.badRequest().body("不能更新自己的角色");
            }

            MerchantUserMapping mapping = merchantService.updateEmployeeRole(merchantUid, userId, newRole);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("mappingId", mapping.getId());
            result.put("newRole", newRole);
            result.put("message", "员工角色更新成功");

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("更新员工角色失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("更新员工角色失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{merchantUid}/employees/{userId}")
    @Operation(summary = "移除商家员工")
    public ResponseEntity<?> removeEmployee(
            @PathVariable String merchantUid,
            @PathVariable Long userId,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到移除员工请求，merchantUid: {}, userId: {}", merchantUid, userId);

        User currentUser = authenticationService.getAuthenticatedUser(request, response);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            // 验证权限
            if (!merchantService.isUserHasRequiredRole(currentUser.getId(), merchantUid, MerchantUserRole.ADMIN)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("您没有权限移除员工，需要管理员以上权限");
            }

            // 不能移除自己
            if (currentUser.getId().equals(userId)) {
                return ResponseEntity.badRequest().body("不能移除自己，如需退出请联系其他管理员");
            }

            merchantService.removeEmployee(merchantUid, userId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "员工已成功移除");

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("移除员工失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("移除员工失败: " + e.getMessage());
        }
    }

    @PostMapping("/verification")
    @Operation(summary = "商家实名认证")
    public ResponseEntity<?> verifyMerchant(
            @RequestParam Long userId,
            @RequestParam String businessLicense,
            @RequestParam String realName,
            @RequestParam MultipartFile licenseImage,
            @RequestParam String contactPhone,
            @RequestParam String businessAddress,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到商家实名认证请求，用户ID: {}", userId);

        User currentUser = authenticationService.getAuthenticatedUser(request, response);
        if (currentUser == null || !currentUser.getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            MerchantInfo merchant = merchantService.verifyMerchant(
                    userId, businessLicense, realName, licenseImage, contactPhone, businessAddress);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("merchantId", merchant.getId());
            result.put("merchantUid", merchant.getMerchantUid());
            result.put("message", "商家认证申请已提交，等待审核");

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("商家实名认证失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("商家实名认证失败: " + e.getMessage());
        }
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "获取用户的商家列表")
    public ResponseEntity<?> getUserMerchants(
            @PathVariable Long userId,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到获取用户商家列表请求，用户ID: {}", userId);

        User currentUser = authenticationService.getAuthenticatedUser(request, response);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 只能查看自己的商家列表，或管理员可以查看所有
        if (!currentUser.getId().equals(userId) && !"admin".equals(currentUser.getUserGroup())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            List<MerchantInfo> merchants = merchantService.getUserMerchants(userId);
            return ResponseEntity.ok(merchants);
        } catch (Exception e) {
            logger.error("获取用户商家列表失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("获取用户商家列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/invitations")
    @Operation(summary = "获取待接受的商家邀请")
    public ResponseEntity<?> getPendingInvitations(
            HttpServletRequest request,
            HttpServletResponse response) {

        User currentUser = authenticationService.getAuthenticatedUser(request, response);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            List<MerchantUserMapping> invitations = merchantService.getPendingInvitations(currentUser.getId());
            return ResponseEntity.ok(invitations);
        } catch (Exception e) {
            logger.error("获取待接受邀请失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("获取待接受邀请失败: " + e.getMessage());
        }
    }

    @PostMapping("/invitations/{mappingId}/accept")
    @Operation(summary = "接受商家邀请")
    public ResponseEntity<?> acceptInvitation(
            @PathVariable Long mappingId,
            HttpServletRequest request,
            HttpServletResponse response) {

        User currentUser = authenticationService.getAuthenticatedUser(request, response);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            MerchantUserMapping mapping = merchantService.acceptInvitation(mappingId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "已成功接受邀请");

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("接受邀请失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("接受邀请失败: " + e.getMessage());
        }
    }

    @PostMapping("/invitations/{mappingId}/reject")
    @Operation(summary = "拒绝商家邀请")
    public ResponseEntity<?> rejectInvitation(
            @PathVariable Long mappingId,
            HttpServletRequest request,
            HttpServletResponse response) {

        User currentUser = authenticationService.getAuthenticatedUser(request, response);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            merchantService.rejectInvitation(mappingId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "已拒绝邀请");

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("拒绝邀请失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("拒绝邀请失败: " + e.getMessage());
        }
    }

    /**
     * 获取待审核的商家列表
     */
    @GetMapping("/pending")
    @Operation(summary = "获取待审核的商家列表")
    public ResponseEntity<?> getPendingMerchants(
            HttpServletRequest request,
            HttpServletResponse response) {

        User currentUser = authenticationService.getAuthenticatedUser(request, response);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 只有管理员可以查看待审核商家
        if (!userService.isAdminUser(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            List<MerchantInfo> pendingMerchants = merchantService.getPendingMerchants();

            // 修改后：
            List<MerchantResponseDTO> merchantDTOs = pendingMerchants.stream()
                    .map(MerchantResponseDTO::fromEntity)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(merchantDTOs);
        } catch (Exception e) {
            logger.error("获取待审核商家列表失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("获取待审核商家列表失败: " + e.getMessage());
        }
    }

    /**
     * 审核商家认证
     */
    @PostMapping("/{merchantUid}/audit")
    @Operation(summary = "审核商家认证")
    public ResponseEntity<?> auditMerchant(
            @PathVariable String merchantUid,
            @RequestParam boolean approved,
            @RequestParam(required = false) String remarks,
            HttpServletRequest request,
            HttpServletResponse response) {

        User currentUser = authenticationService.getAuthenticatedUser(request, response);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 只有管理员可以审核商家
        if (!userService.isAdminUser(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            MerchantInfo merchant = merchantService.auditMerchantVerification(merchantUid, approved, remarks);

            // 使用静态工厂方法创建DTO
            MerchantResponseDTO merchantDTO = MerchantResponseDTO.fromEntity(merchant);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("merchant", merchantDTO);
            result.put("message", approved ? "商家认证已审核通过" : "商家认证已被拒绝");

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("审核商家失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("审核商家失败: " + e.getMessage());
        }
    }
}