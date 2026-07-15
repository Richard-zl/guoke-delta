package com.delta.product.service;

import com.delta.product.entity.Product;

/**
 * 体验单规则：识别、优惠券拦截、限购校验
 */
public interface TrialOrderService {

    /** 配置键：体验单根分类 ID */
    String CONFIG_ROOT_CATEGORY_ID = "trial.root_category_id";
    /** 配置键：限购周期（天数，0=不限） */
    String CONFIG_LIMIT_PERIOD = "trial.limit_period";
    /** 配置键：额外视为体验单的商品分类 ID（逗号分隔，兼容平铺分类结构） */
    String CONFIG_ADDITIONAL_CATEGORY_IDS = "trial.additional_category_ids";
    /** 默认体验单根分类：超值体验单（含子分类 46 端游、54 手游等） */
    long DEFAULT_ROOT_CATEGORY_ID = 38L;

    boolean isTrialProduct(Product product);

    void validateNoCoupon(Product product, Long couponId);

    /** 下单前校验：周期内已有已支付体验单则拒绝 */
    void validateTrialOrderForCreate(Long userId, Product product);

    /** 支付前校验：排除当前订单后，周期内已有其他已支付体验单则拒绝 */
    void validateTrialOrderForPay(Long userId, Long orderId, Product product);

    /** 填充商品详情上的体验单展示字段 */
    void enrichTrialInfo(Product product, Long userId);
}
