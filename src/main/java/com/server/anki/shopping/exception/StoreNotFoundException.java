package com.server.anki.shopping.exception;

/**
 * 店铺未找到异常
 * 当尝试访问或操作不存在的店铺时抛出此异常
 */
public class StoreNotFoundException extends ShoppingException {

    private static final String ERROR_CODE = "STORE_NOT_FOUND";

    /**
     * 创建一个店铺未找到异常
     * @param storeId 店铺ID
     */
    public StoreNotFoundException(Long storeId) {
        super(String.format("店铺不存在，ID: %d", storeId), ERROR_CODE, false);
    }

    /**
     * 创建一个店铺未找到异常
     * @param storeName 店铺名称
     * @param message 自定义错误消息
     */
    public StoreNotFoundException(String storeName, String message) {
        super(String.format("店铺 '%s' %s", storeName, message), ERROR_CODE, false);
    }
}