package com.delta.common.chat.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 规范化后的会话表实体，对应 chat_conversation
 */
@Data
@TableName("chat_conversation")
public class ChatConversation {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String type;        // USER_PLAYER / USER_CS / PLAYER_CS / PLAYER_PLAYER

    private Long orderId;

    private String status;      // ACTIVE / CLOSED

    private Long lastMessageId;

    private LocalDateTime lastMessageAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 下列字段为列表展示用的派生字段，不落库 */
    @TableField(exist = false)
    private Long targetId;

    @TableField(exist = false)
    private String targetName;

    @TableField(exist = false)
    private String avatar;

    @TableField(exist = false)
    private String lastMessage;

    @TableField(exist = false)
    private Integer unreadCount;
}

