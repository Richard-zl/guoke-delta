package com.delta.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("recommend_category")
public class RecommendCategory {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 编码，如 HOT、ESCORT */
    private String code;
    /** 展示名称，如 热门推荐、护航专区推荐 */
    private String name;
    private Integer sortOrder;
}
