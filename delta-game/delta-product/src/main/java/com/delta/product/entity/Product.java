package com.delta.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.delta.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product")
public class Product extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long categoryId;
    private String name;
    private String subtitle;
    private String description;
    private String coverImage;
    private String images;
    private BigDecimal price;
    private Integer status;
    private Integer sortOrder;
    private Integer salesCount;
    private Integer isRecommend;
    /** 所属热门推荐分类（isRecommend=1 时可选），为空则仅按 isRecommend 展示在「全部」 */
    private Long recommendCategoryId;
    /** 商品平均评分 */
    private java.math.BigDecimal avgRating;
    /** 评价数量 */
    private Integer reviewCount;
    /** 商品级抽佣比例(0~1)，为null则使用系统默认 */
    private java.math.BigDecimal commissionRate;
    /** 每人限购类型（0-不限购 1-永久限购一次 2-7天限购一次 3-1个月限购一次） */
    private Integer perUserLimitType;
    /** 是否开启每人限购（兼容旧字段，保存时自动同步） */
    private Integer perUserLimitEnabled;
    /** 每个用户最多可购买次数（兼容旧字段，保存时自动同步为 1） */
    private Integer perUserLimitCount;
    @TableLogic
    private Integer deleted;

    /** 是否允许使用优惠券（非DB字段，体验单为 false） */
    @TableField(exist = false)
    private Boolean couponAllowed;

    /** 体验单限购是否已达上限（非DB字段） */
    @TableField(exist = false)
    private Boolean trialLimitReached;

    /** 体验单限购提示文案（非DB字段） */
    @TableField(exist = false)
    private String trialLimitTip;

    /** 是否允许会员等级折扣（非DB字段，排除分类为 false） */
    @TableField(exist = false)
    private Boolean memberDiscountAllowed;

    /** 当前用户会员折扣率（非DB字段，1.00 表示无折扣） */
    @TableField(exist = false)
    private BigDecimal memberDiscountRate;

    /** 当前用户会员等级代码（非DB字段） */
    @TableField(exist = false)
    private String memberLevelCode;

    /** 当前用户会员等级名称（非DB字段） */
    @TableField(exist = false)
    private String memberLevelName;

    /** 是否可选数量: 1开 0关 */
    private Integer quantityEnabled;
    /** 数量单位名(如:小时/局/个) */
    private String unitLabel;
    /** 最大可购数量 */
    private Integer maxQuantity;

    /** 规格选项列表（非DB字段，详情/保存用） */
    @TableField(exist = false)
    private java.util.List<ProductVariant> variants;
}
