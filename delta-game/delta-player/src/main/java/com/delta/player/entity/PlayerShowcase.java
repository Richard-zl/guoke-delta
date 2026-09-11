package com.delta.player.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.delta.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("player_showcase")
public class PlayerShowcase extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long playerId;
    private String coverUrl;
    private String tagline;
    private String bio;
    /** 仅用于风采页的展示评分，空则使用真实评分。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal displayRating;
    /** 仅用于风采页的展示完成单，空则实时统计。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer displayCompletedOrders;
    /** 仅用于风采页的展示完成率，空则使用真实完成率。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal displayCompleteRate;
    private String selectedImages;
    private Integer sortOrder;
    /** 0下架 1上架 */
    private Integer status;
}
