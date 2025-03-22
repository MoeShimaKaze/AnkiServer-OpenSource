package com.server.anki.shopping.exception;

/**
 * 商品未找到异常
 * 当尝试访问或操作不存在的商品时抛出此异常
 */
public class ProductNotFoundException extends ShoppingException {

    private static final String ERROR_CODE = "PRODUCT_NOT_FOUND";

    /**
     * 创建一个商品未找到异常
     * @param productId 商品ID
     */
    public ProductNotFoundException(Long productId) {
        super(String.format("商品不存在，ID: %d", productId), ERROR_CODE, false);
    }

    /**
     * 创建一个商品未找到异常
     * @param productName 商品名称
     * @param storeId 店铺ID
     */
    public ProductNotFoundException(String productName, Long storeId) {
        super(String.format("商品 '%s' 在店铺(ID: %d)中不存在", productName, storeId),
                ERROR_CODE, false);
    }
}