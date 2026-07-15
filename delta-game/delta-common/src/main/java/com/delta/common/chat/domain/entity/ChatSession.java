package com.delta.common.chat.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.delta.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/**
 * 私聊会话：仅 id1/id2 两个参与者，id1 < id2
 * 编码规则见 ChatParticipantId
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chat_session")
public class ChatSession extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long id1;        // 参与者1编码ID(较小)
    private Long id2;        // 参与者2编码ID(较大)
    private String status;   // ACTIVE/CLOSED
    private LocalDateTime lastMessageAt;

    /** 对方编码ID（非DB字段，列表展示用） */
    @TableField(exist = false)
    private Long targetId;
    /** 对方昵称（非DB字段） */
    @TableField(exist = false)
    private String targetName;
    /** 对方头像（非DB字段） */
    @TableField(exist = false)
    private String avatar;
    /** 最后一条消息内容（非DB字段） */
    @TableField(exist = false)
    private String lastMessage;
    /** 未读消息数（非DB字段） */
    @TableField(exist = false)
    private Integer unreadCount;
}
