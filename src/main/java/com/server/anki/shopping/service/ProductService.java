package com.server.anki.shopping.service;

import com.server.anki.shopping.dto.ProductDTO;
import com.server.anki.shopping.entity.Product;
import com.server.anki.shopping.entity.Store;
import com.server.anki.shopping.enums.ProductStatus;
import com.server.anki.shopping.exception.InvalidOperationException;
import com.server.anki.shopping.exception.ProductNotFoundException;
import com.server.anki.shopping.exception.StoreNotFoundException;
import com.server.anki.shopping.repository.ProductRepository;
import com.server.anki.shopping.repository.StoreRepository;
import com.server.anki.shopping.service.validator.ProductValidator;
import com.server.anki.storage.MinioService;
import com.server.anki.user.User;
import com.server.anki.user.UserService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 商品管理服务
 * 负责处理商品的上架、更新、搜索等相关业务逻辑
 */
@Slf4j
@Service
public class ProductService {
    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private ProductValidator productValidator;

    @Autowired
    private StoreService storeService;

    @Autowired
    private UserService userService;

    @Autowired
    private MinioService minioService;

    /**
     * 创建新商品
     * 处理商家的商品上架请求，包括信息验证、保证金验证和状态设置
     */
    @Transactional
    public Product createProduct(ProductDTO productDTO, MultipartFile imageFile) {
        logger.info("开始处理商品创建请求，店铺ID: {}", productDTO.getStoreId());

        // 获取店铺信息
        Store store = storeRepository.findById(productDTO.getStoreId())
                .orElseThrow(() -> new StoreNotFoundException(productDTO.getStoreId()));

        // 验证商品信息
        productValidator.validateCreateProduct(store, productDTO);

        // 验证商家是否已缴纳保证金（管理员无需验证）
        User merchant = store.getMerchant();
        if (!userService.isAdminUser(merchant) && !storeService.hasSecurityDeposit(merchant.getId())) {
            logger.warn("商家 {} 未缴纳保证金，无法创建商品", merchant.getId());
            throw new InvalidOperationException("商家未缴纳保证金，无法创建商品");
        }

        // 创建商品实体，填充所有字段
        Product product = new Product();
        product.setStore(store);
        product.setName(productDTO.getName());
        product.setDescription(productDTO.getDescription());
        product.setPrice(productDTO.getPrice());
        product.setStock(productDTO.getStock());
        product.setCategory(productDTO.getCategory());
        product.setStatus(productDTO.getStatus() != null ? productDTO.getStatus() : ProductStatus.PENDING_REVIEW);

        // 设置商品物理属性
        product.setWeight(productDTO.getWeight() != null ? productDTO.getWeight() : 1.0);
        product.setLength(productDTO.getLength());
        product.setWidth(productDTO.getWidth());
        product.setHeight(productDTO.getHeight());
        product.setLargeItem(productDTO.isLargeItem() != null ? productDTO.isLargeItem() : false);
        product.setFragile(productDTO.isFragile() != null ? productDTO.isFragile() : false);
        product.setNeedsPackaging(productDTO.isNeedsPackaging() != null ? productDTO.isNeedsPackaging() : false);

        // 设置商品价格相关信息
        product.setCostPrice(productDTO.getCostPrice());
        product.setMarketPrice(productDTO.getMarketPrice());
        product.setWholesalePrice(productDTO.getWholesalePrice());

        // 设置商品编码和销售信息
        product.setBarcode(productDTO.getBarcode());
        product.setSkuCode(productDTO.getSkuCode());
        product.setMaxBuyLimit(productDTO.getMaxBuyLimit());
        product.setMinBuyLimit(productDTO.getMinBuyLimit() != null ? productDTO.getMinBuyLimit() : 1);
        product.setRating(productDTO.getRating() != null ? productDTO.getRating() : 5.0);
        product.setSalesCount(productDTO.getSalesCount() != null ? productDTO.getSalesCount() : 0);
        product.setViewCount(productDTO.getViewCount() != null ? productDTO.getViewCount() : 0);

        // 处理商品图片上传
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                String imageUrl = minioService.uploadFile(
                        imageFile,
                        "products",
                        String.format("product_%d_%d", store.getId(), System.currentTimeMillis()),
                        true
                );
                product.setImageUrl(imageUrl);
                logger.info("商品图片上传成功: {}", imageUrl);
            } catch (Exception e) {
                logger.error("商品图片上传失败: {}", e.getMessage(), e);
                throw new InvalidOperationException("商品图片上传失败: " + e.getMessage());
            }
        }

        // 保存商品信息
        Product savedProduct = productRepository.save(product);
        logger.info("商品创建成功，商品ID: {}", savedProduct.getId());

        return savedProduct;
    }

    @NotNull
    private static Product getProduct(ProductDTO productDTO, Store store) {
        Product product = new Product();
        product.setStore(store);
        product.setName(productDTO.getName());
        product.setDescription(productDTO.getDescription());
        product.setPrice(productDTO.getPrice());
        product.setStock(productDTO.getStock());
        product.setCategory(productDTO.getCategory());
        product.setImageUrl(productDTO.getImageUrl());
        product.setStatus(ProductStatus.PENDING_REVIEW);

        // 设置商品重量
        if (productDTO.getWeight() != null) {
            product.setWeight(productDTO.getWeight());
        } else {
            // 设置默认重量为1.0kg
            product.setWeight(1.0);
        }
        return product;
    }

    /**
     * 更新商品信息
     * 允许商家更新商品的基本信息，需要验证更新的合法性
     */
    @Transactional
    public Product updateProduct(Long productId, ProductDTO productDTO, MultipartFile imageFile) {
        logger.info("开始更新商品信息，商品ID: {}", productId);

        Product existingProduct = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        // 验证更新请求
        productValidator.validateUpdateProduct(existingProduct, productDTO);

        // 更新商品基本信息
        existingProduct.setName(productDTO.getName());
        existingProduct.setDescription(productDTO.getDescription());
        existingProduct.setPrice(productDTO.getPrice());
        existingProduct.setStock(productDTO.getStock());
        existingProduct.setCategory(productDTO.getCategory());

        // 如果提供了status且为有效值，则更新状态
        if (productDTO.getStatus() != null) {
            existingProduct.setStatus(productDTO.getStatus());
        }

        // 更新商品物理属性
        if (productDTO.getWeight() != null) {
            existingProduct.setWeight(productDTO.getWeight());
        }

        if (productDTO.getLength() != null) existingProduct.setLength(productDTO.getLength());
        if (productDTO.getWidth() != null) existingProduct.setWidth(productDTO.getWidth());
        if (productDTO.getHeight() != null) existingProduct.setHeight(productDTO.getHeight());

        if (productDTO.getIsLargeItem() != null) existingProduct.setIsLargeItem(productDTO.getIsLargeItem());
        if (productDTO.getIsFragile() != null) existingProduct.setIsFragile(productDTO.getIsFragile());
        if (productDTO.getNeedsPackaging() != null) existingProduct.setNeedsPackaging(productDTO.getNeedsPackaging());

        // 更新商品价格相关信息
        if (productDTO.getCostPrice() != null) existingProduct.setCostPrice(productDTO.getCostPrice());
        if (productDTO.getMarketPrice() != null) existingProduct.setMarketPrice(productDTO.getMarketPrice());
        if (productDTO.getWholesalePrice() != null) existingProduct.setWholesalePrice(productDTO.getWholesalePrice());

        // 更新商品编码和销售信息
        if (productDTO.getBarcode() != null) existingProduct.setBarcode(productDTO.getBarcode());
        if (productDTO.getSkuCode() != null) existingProduct.setSkuCode(productDTO.getSkuCode());
        if (productDTO.getMaxBuyLimit() != null) existingProduct.setMaxBuyLimit(productDTO.getMaxBuyLimit());
        if (productDTO.getMinBuyLimit() != null) existingProduct.setMinBuyLimit(productDTO.getMinBuyLimit());

        // 处理商品图片上传
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                String imageUrl = minioService.uploadFile(
                        imageFile,
                        "products",
                        String.format("product_%d_%d", existingProduct.getStore().getId(), System.currentTimeMillis()),
                        true
                );
                existingProduct.setImageUrl(imageUrl);
                logger.info("商品图片更新成功: {}", imageUrl);
            } catch (Exception e) {
                logger.error("商品图片更新失败: {}", e.getMessage(), e);
                throw new InvalidOperationException("商品图片更新失败: " + e.getMessage());
            }
        }

        // 保存更新
        Product updatedProduct = productRepository.save(existingProduct);
        logger.info("商品信息更新成功，商品ID: {}", productId);

        return updatedProduct;
    }

    /**
     * 更新商品状态
     * 处理商品的上架、下架等状态变更，确保只有缴纳保证金的商家才能上架商品
     */
    @Transactional
    public void updateProductStatus(Long productId, ProductStatus newStatus) {
        logger.info("更新商品状态，商品ID: {}, 新状态: {}", productId, newStatus);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        // 验证状态变更的合法性
        validateStatusTransition(product.getStatus(), newStatus);

        // 如果要将商品状态变更为上架，需要验证商家是否已缴纳保证金
        if (newStatus == ProductStatus.ON_SALE) {
            if (!storeService.hasSecurityDeposit(product.getStore().getMerchant().getId())) {
                logger.warn("商家 {} 未缴纳保证金，无法上架商品", product.getStore().getMerchant().getId());
                throw new InvalidOperationException("商家未缴纳保证金，无法上架商品");
            }
        }

        product.setStatus(newStatus);
        productRepository.save(product);
    }

    /**
     * 更新商品库存
     * 处理商品库存的增减操作
     */
    @Transactional
    public void updateStock(Long productId, int quantity) {
        logger.info("更新商品库存，商品ID: {}, 变更数量: {}", productId, quantity);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        // 检查库存变更的合法性
        int newStock = product.getStock() + quantity;
        if (newStock < 0) {
            throw new InvalidOperationException("库存不足");
        }

        product.setStock(newStock);
        productRepository.save(product);

        // 如果库存为0，自动更新商品状态为缺货
        if (newStock == 0) {
            updateProductStatus(productId, ProductStatus.OUT_OF_STOCK);
        }
    }

    /**
     * 根据ID获取商品
     *
     * @param productId 商品ID
     * @return 商品信息
     */
    public Product getProductById(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    /**
     * 搜索商品
     * 基于关键词搜索商品
     */
    public Page<Product> searchProducts(String keyword, ProductStatus status, Pageable pageable) {
        return productRepository.searchProducts(keyword, status, pageable);
    }

    /**
     * 获取店铺的所有商品
     */
    public Page<Product> getStoreProducts(Long storeId, Pageable pageable) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new StoreNotFoundException(storeId));
        // 直接查询该店铺的所有商品，不再限制状态
        return productRepository.findByStore(store, pageable);
    }

    /**
     * 验证商品状态变更的合法性
     */
    private void validateStatusTransition(ProductStatus currentStatus, ProductStatus newStatus) {
        // 下架的商品不能直接变为在售状态
        if (currentStatus == ProductStatus.DISCONTINUED && newStatus == ProductStatus.ON_SALE) {
            throw new InvalidOperationException("下架商品需要重新审核才能上架");
        }

        // 待审核商品只能变为在售或已拒绝状态
        if (currentStatus == ProductStatus.PENDING_REVIEW &&
                newStatus != ProductStatus.ON_SALE &&
                newStatus != ProductStatus.REJECTED) {
            throw new InvalidOperationException("待审核商品只能变更为在售或已拒绝状态");
        }
    }
}