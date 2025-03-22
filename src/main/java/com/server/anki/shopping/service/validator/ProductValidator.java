package com.server.anki.shopping.service.validator;

import com.server.anki.shopping.dto.ProductDTO;
import com.server.anki.shopping.entity.Product;
import com.server.anki.shopping.entity.Store;
import com.server.anki.shopping.enums.ProductStatus;
import com.server.anki.shopping.exception.InvalidOperationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 商品信息验证器
 * 负责验证商品相关的业务规则
 */
@Component
public class ProductValidator {

    /**
     * 验证商品创建请求
     */
    public void validateCreateProduct(Store store, ProductDTO productDTO) {
        // 验证店铺状态
        validateStoreStatus(store);

        // 验证商品价格
        validatePrice(productDTO.getPrice());

        // 验证商品库存
        validateStock(productDTO.getStock());

        // 验证商品分类
        if (productDTO.getCategory() == null) {
            throw new InvalidOperationException("商品分类不能为空");
        }
    }

    /**
     * 验证商品更新请求
     */
    public void validateUpdateProduct(Product existingProduct, ProductDTO productDTO) {
        // 验证商品状态
        if (existingProduct.getStatus() == ProductStatus.DISCONTINUED) {
            throw new InvalidOperationException("已下架商品不能修改");
        }

        // 验证商品价格
        validatePrice(productDTO.getPrice());

        // 验证商品库存
        validateStock(productDTO.getStock());
    }

    /**
     * 验证商品上架操作
     */
    public void validateProductListing(Product product) {
        // 验证商品信息完整性
        if (product.getPrice() == null || product.getStock() == null) {
            throw new InvalidOperationException("商品信息不完整，无法上架");
        }

        // 验证商品库存
        if (product.getStock() <= 0) {
            throw new InvalidOperationException("商品库存不足，无法上架");
        }
    }

    private void validateStoreStatus(Store store) {
        if (!store.getStatus().equals(com.server.anki.shopping.enums.StoreStatus.ACTIVE)) {
            throw new InvalidOperationException("店铺未处于营业状态，无法创建商品");
        }
    }

    private void validatePrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOperationException("商品价格必须大于0");
        }
    }

    private void validateStock(Integer stock) {
        if (stock == null || stock < 0) {
            throw new InvalidOperationException("商品库存不能小于0");
        }
    }
}