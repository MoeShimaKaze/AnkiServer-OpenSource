package com.server.anki.shopping.service;

import com.server.anki.message.MessageType;
import com.server.anki.message.service.MessageService;
import com.server.anki.shopping.entity.Product;
import com.server.anki.shopping.entity.Store;
import com.server.anki.shopping.enums.ProductStatus;
import com.server.anki.shopping.exception.InvalidOperationException;
import com.server.anki.shopping.exception.ProductNotFoundException;
import com.server.anki.shopping.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 商品审核服务扩展
 * 提供管理员审核商品的相关功能
 */
@Service
public class AdminProductService {
    private static final Logger logger = LoggerFactory.getLogger(AdminProductService.class);

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private MessageService messageService;

    /**
     * 获取待审核商品列表
     * @param pageable 分页参数
     * @return 待审核商品分页列表
     */
    public Page<Product> getPendingReviewProducts(Pageable pageable) {
        logger.info("获取待审核商品列表, 页码: {}, 每页大小: {}", pageable.getPageNumber(), pageable.getPageSize());
        return productRepository.findByStatus(ProductStatus.PENDING_REVIEW, pageable);
    }

    /**
     * 审核商品
     * @param productId 商品ID
     * @param approved 是否通过审核
     * @param remarks 审核备注
     * @return 更新后的商品信息
     */
    @Transactional
    public Product reviewProduct(Long productId, boolean approved, String remarks) {
        logger.info("审核商品, 商品ID: {}, 是否通过: {}", productId, approved);

        // 获取商品信息
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        // 验证商品状态
        if (product.getStatus() != ProductStatus.PENDING_REVIEW) {
            throw new InvalidOperationException("只能审核待审核状态的商品");
        }

        // 设置新状态
        ProductStatus newStatus = approved ? ProductStatus.ON_SALE : ProductStatus.REJECTED;

        // 使用现有的updateProductStatus方法更新状态
        productService.updateProductStatus(productId, newStatus);

        // 重新获取更新后的商品
        Product updatedProduct = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        // 存储审核备注
        if (remarks != null && !remarks.trim().isEmpty()) {
            // 使用专门的reviewRemark字段存储审核备注
            updatedProduct.setReviewRemark(remarks);

            // 记录审核时间和审核员ID
            updatedProduct.setReviewedAt(LocalDateTime.now());
            // adminId可以从上下文或参数中获取
            // updatedProduct.setReviewerId(adminId);

            updatedProduct = productRepository.save(updatedProduct);
        }

        // 发送通知给商家
        Store store = updatedProduct.getStore();
        if (store != null && store.getMerchant() != null) {
            String statusText = approved ? "已通过审核，已上架" : "未通过审核，已拒绝";
            String remarkInfo = remarks != null && !remarks.trim().isEmpty()
                    ? "，审核备注: " + remarks
                    : "";

            messageService.sendMessage(
                    store.getMerchant(),
                    String.format("您的商品 '%s' %s%s",
                            updatedProduct.getName(),
                            statusText,
                            remarkInfo),
                    MessageType.PRODUCT_STATUS_UPDATED,
                    null
            );
        }

        logger.info("商品审核完成, 商品ID: {}, 新状态: {}", productId, newStatus);
        return updatedProduct;
    }

    /**
     * 更新商品信息
     * @param product 商品实体
     * @return 更新后的商品
     */
    @Transactional
    public Product updateProduct(Product product) {
        logger.debug("更新商品信息，商品ID: {}", product.getId());
        return productRepository.save(product);
    }

    /**
     * 获取各状态商品数量
     * @return 包含各状态商品数量的映射
     */
    public ProductStatusCount getProductStatusCount() {
        long pendingReviewCount = productRepository.countByStatus(ProductStatus.PENDING_REVIEW);
        long onSaleCount = productRepository.countByStatus(ProductStatus.ON_SALE);
        long outOfStockCount = productRepository.countByStatus(ProductStatus.OUT_OF_STOCK);
        long rejectedCount = productRepository.countByStatus(ProductStatus.REJECTED);

        return new ProductStatusCount(
                pendingReviewCount,
                onSaleCount,
                outOfStockCount,
                rejectedCount
        );
    }

    /**
         * 商品状态统计类
         */
        public record ProductStatusCount(long pendingReview, long onSale, long outOfStock, long rejected) {

        public long getTotal() {
                return pendingReview + onSale + outOfStock + rejected;
            }
        }
}