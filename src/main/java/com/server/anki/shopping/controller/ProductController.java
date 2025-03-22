package com.server.anki.shopping.controller;

import com.server.anki.auth.AuthenticationService;
import com.server.anki.shopping.controller.response.ProductResponse;
import com.server.anki.shopping.dto.ProductDTO;
import com.server.anki.shopping.entity.Product;
import com.server.anki.shopping.enums.ProductCategory;
import com.server.anki.shopping.enums.ProductStatus;
import com.server.anki.shopping.exception.ProductNotFoundException;
import com.server.anki.shopping.service.ProductService;
import com.server.anki.user.User;
import com.server.anki.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;


@Slf4j
@RestController
@RequestMapping("/api/products")
@Tag(name = "商品管理", description = "商品创建、更新、查询相关接口")
public class ProductController {
    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    @Autowired
    private ProductService productService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserService userService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "创建商品")
    public ResponseEntity<ProductResponse> createProduct(
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile,
            @RequestParam("storeId") Long storeId,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("price") BigDecimal price,
            @RequestParam("stock") Integer stock,
            @RequestParam("category") ProductCategory category,
            @RequestParam(value = "weight", required = false) Double weight,
            @RequestParam(value = "status", required = false) String statusStr,
            // 可选参数
            @RequestParam(value = "length", required = false) Double length,
            @RequestParam(value = "width", required = false) Double width,
            @RequestParam(value = "height", required = false) Double height,
            @RequestParam(value = "isLargeItem", required = false) Boolean isLargeItem,
            @RequestParam(value = "isFragile", required = false) Boolean isFragile,
            @RequestParam(value = "needsPackaging", required = false) Boolean needsPackaging,
            @RequestParam(value = "costPrice", required = false) BigDecimal costPrice,
            @RequestParam(value = "marketPrice", required = false) BigDecimal marketPrice,
            @RequestParam(value = "wholesalePrice", required = false) BigDecimal wholesalePrice,
            @RequestParam(value = "barcode", required = false) String barcode,
            @RequestParam(value = "skuCode", required = false) String skuCode,
            @RequestParam(value = "maxBuyLimit", required = false) Integer maxBuyLimit,
            @RequestParam(value = "minBuyLimit", required = false) Integer minBuyLimit) {

        logger.info("收到商品创建请求，店铺ID: {}", storeId);

        // 构建ProductDTO
        ProductDTO productDTO = new ProductDTO();
        productDTO.setStoreId(storeId);
        productDTO.setName(name);
        productDTO.setDescription(description);
        productDTO.setPrice(price);
        productDTO.setStock(stock);
        productDTO.setCategory(category);
        productDTO.setWeight(weight != null ? weight : 1.0);

        // 处理状态字段
        ProductStatus status = convertStringToStatus(statusStr);
        productDTO.setStatus(status != null ? status : ProductStatus.PENDING_REVIEW);

        // 设置可选物理属性字段
        productDTO.setLength(length);
        productDTO.setWidth(width);
        productDTO.setHeight(height);
        productDTO.setIsLargeItem(isLargeItem);
        productDTO.setIsFragile(isFragile);
        productDTO.setNeedsPackaging(needsPackaging);

        // 设置可选价格相关字段
        productDTO.setCostPrice(costPrice);
        productDTO.setMarketPrice(marketPrice);
        productDTO.setWholesalePrice(wholesalePrice);

        // 设置可选编码和销售相关字段
        productDTO.setBarcode(barcode);
        productDTO.setSkuCode(skuCode);
        productDTO.setMaxBuyLimit(maxBuyLimit);
        productDTO.setMinBuyLimit(minBuyLimit);

        // 调用service方法，传递文件和DTO
        Product product = productService.createProduct(productDTO, imageFile);

        return ResponseEntity.ok(ProductResponse.fromProduct(product));
    }

    @PutMapping(value = "/{productId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "更新商品信息")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long productId,
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile,
            @RequestParam("storeId") Long storeId,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("price") BigDecimal price,
            @RequestParam("stock") Integer stock,
            @RequestParam("category") ProductCategory category,
            @RequestParam(value = "weight", required = false) Double weight,
            @RequestParam(value = "status", required = false) String statusStr,
            // 可选参数
            @RequestParam(value = "length", required = false) Double length,
            @RequestParam(value = "width", required = false) Double width,
            @RequestParam(value = "height", required = false) Double height,
            @RequestParam(value = "isLargeItem", required = false) Boolean isLargeItem,
            @RequestParam(value = "isFragile", required = false) Boolean isFragile,
            @RequestParam(value = "needsPackaging", required = false) Boolean needsPackaging,
            @RequestParam(value = "costPrice", required = false) BigDecimal costPrice,
            @RequestParam(value = "marketPrice", required = false) BigDecimal marketPrice,
            @RequestParam(value = "wholesalePrice", required = false) BigDecimal wholesalePrice,
            @RequestParam(value = "barcode", required = false) String barcode,
            @RequestParam(value = "skuCode", required = false) String skuCode,
            @RequestParam(value = "maxBuyLimit", required = false) Integer maxBuyLimit,
            @RequestParam(value = "minBuyLimit", required = false) Integer minBuyLimit) {

        logger.info("收到商品信息更新请求，商品ID: {}", productId);

        // 构建ProductDTO
        ProductDTO productDTO = new ProductDTO();
        productDTO.setStoreId(storeId);
        productDTO.setName(name);
        productDTO.setDescription(description);
        productDTO.setPrice(price);
        productDTO.setStock(stock);
        productDTO.setCategory(category);
        productDTO.setWeight(weight);

        // 处理状态字段
        ProductStatus status = convertStringToStatus(statusStr);
        if (status != null) {
            productDTO.setStatus(status);
        }

        // 设置可选物理属性字段
        productDTO.setLength(length);
        productDTO.setWidth(width);
        productDTO.setHeight(height);
        productDTO.setIsLargeItem(isLargeItem);
        productDTO.setIsFragile(isFragile);
        productDTO.setNeedsPackaging(needsPackaging);

        // 设置可选价格相关字段
        productDTO.setCostPrice(costPrice);
        productDTO.setMarketPrice(marketPrice);
        productDTO.setWholesalePrice(wholesalePrice);

        // 设置可选编码和销售相关字段
        productDTO.setBarcode(barcode);
        productDTO.setSkuCode(skuCode);
        productDTO.setMaxBuyLimit(maxBuyLimit);
        productDTO.setMinBuyLimit(minBuyLimit);

        // 调用service方法，传递文件和DTO
        Product product = productService.updateProduct(productId, productDTO, imageFile);

        return ResponseEntity.ok(ProductResponse.fromProduct(product));
    }

    @PutMapping("/{productId}/status")
    @Operation(summary = "更新商品状态")
    public ResponseEntity<Void> updateProductStatus(
            @PathVariable Long productId,
            @RequestParam ProductStatus status) {
        productService.updateProductStatus(productId, status);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{productId}/stock")
    @Operation(summary = "更新商品库存")
    public ResponseEntity<Void> updateStock(
            @PathVariable Long productId,
            @RequestParam int quantity) {
        productService.updateStock(productId, quantity);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/search")
    @Operation(summary = "搜索商品")
    public ResponseEntity<Page<ProductResponse>> searchProducts(
            @RequestParam String keyword,
            @RequestParam(required = false) ProductStatus status,
            Pageable pageable) {
        Page<Product> products = productService.searchProducts(keyword, status, pageable);
        return ResponseEntity.ok(products.map(ProductResponse::fromProduct));
    }

    @GetMapping("/store/{storeId}")
    @Operation(summary = "获取店铺的商品列表")
    public ResponseEntity<Page<ProductResponse>> getStoreProducts(
            @PathVariable Long storeId,
            Pageable pageable) {
        Page<Product> products = productService.getStoreProducts(storeId, pageable);
        return ResponseEntity.ok(products.map(ProductResponse::fromProduct));
    }

    /**
     * 获取商品详情
     */
    @GetMapping("/{productId}")
    @Operation(summary = "获取商品详情", description = "获取特定商品的详细信息")
    public ResponseEntity<ProductResponse> getProductDetail(
            @PathVariable Long productId,
            @RequestParam(required = false) Boolean forEdit,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.info("收到获取商品详情请求, 商品ID: {}, 是否用于编辑: {}", productId, forEdit);

        try {
            // 获取商品信息
            Product product = productService.getProductById(productId);

            // 检查是否是编辑请求，并且验证用户是否有权限编辑
            boolean isEditMode = Boolean.TRUE.equals(forEdit);

            // 如果是编辑模式，需要验证用户权限
            if (isEditMode) {
                // 获取当前用户
                User currentUser = authenticationService.getAuthenticatedUser(request, response);
                if (currentUser == null) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
                }

                // 检查是否为商品所属店铺的商家
                boolean isOwner = product.getStore().getMerchant().getId().equals(currentUser.getId());
                boolean isAdmin = userService.isAdminUser(currentUser);

                if (!isOwner && !isAdmin) {
                    // 用户无权编辑该商品
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }

                // 对于编辑模式，返回完整的商品信息，包括所有可编辑字段
                ProductResponse fullResponse = ProductResponse.fromProduct(product);
                logger.info("返回商品完整信息用于编辑, 商品ID: {}", productId);
                return ResponseEntity.ok(fullResponse);
            }

            // 修改后 - 使用不同的变量名
            ProductResponse productResponse = ProductResponse.fromProduct(product);
            logger.info("返回商品基本信息用于展示, 商品ID: {}", productId);
            return ResponseEntity.ok(productResponse);

        } catch (ProductNotFoundException e) {
            logger.error("获取商品详情失败: 商品不存在, ID={}", productId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("获取商品详情失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // 添加辅助方法用于将字符串转换为ProductStatus枚举
    private ProductStatus convertStringToStatus(String statusStr) {
        if (statusStr == null) return null;

        try {
            if (statusStr.equals("true")) {
                return ProductStatus.ON_SALE;
            } else if (statusStr.equals("false")) {
                return ProductStatus.OUT_OF_STOCK;
            }
            return ProductStatus.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            logger.warn("无效的商品状态值: {}", statusStr);
            return ProductStatus.PENDING_REVIEW;
        }
    }
}