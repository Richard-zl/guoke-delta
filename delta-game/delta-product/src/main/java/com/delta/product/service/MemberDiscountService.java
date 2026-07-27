package com.delta.product.service;

import com.delta.product.entity.Product;
import com.delta.product.dto.MemberDiscountResult;

import java.math.BigDecimal;

/**
 * 会员等级折扣：按用户等级对商品原价打折，排除分类可配置
 */
public interface MemberDiscountService {

    /** 配置键：不可享受会员折扣的分类 ID（逗号分隔，含子分类） */
    String CONFIG_EXCLUDE_CATEGORY_IDS = "member.discount_exclude_category_ids";

    /**
     * 判断商品分类是否被排除会员折扣
     */
    boolean isDiscountExcluded(Product product);

    /**
     * 对原价应用会员等级折扣
     *
     * @param subtotal 原价小计
     * @param userId   用户 ID（可空，空则不打折）
     * @param product  商品
     */
    MemberDiscountResult applyLevelDiscount(BigDecimal subtotal, Long userId, Product product);

    /**
     * 填充商品详情上的会员折扣展示字段
     */
    void enrichMemberDiscountInfo(Product product, Long userId);
}
