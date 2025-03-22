package com.server.anki.shopping.exception;

/**
 * 商家未找到异常
 * 当尝试访问或操作不存在的商家信息时抛出此异常
 */
public class MerchantNotFoundException extends ShoppingException {

    private static final String ERROR_CODE = "MERCHANT_NOT_FOUND";

    /**
     * 创建一个商家未找到异常
     * @param merchantId 商家ID
     */
    public MerchantNotFoundException(Long merchantId) {
        super(String.format("商家不存在，ID: %d", merchantId), ERROR_CODE, false);
    }

    /**
     * 创建一个商家未找到异常
     * @param businessLicense 营业执照号
     */
    public MerchantNotFoundException(String businessLicense) {
        super(String.format("未找到营业执照号为 '%s' 的商家信息", businessLicense),
                ERROR_CODE, false);
    }
}