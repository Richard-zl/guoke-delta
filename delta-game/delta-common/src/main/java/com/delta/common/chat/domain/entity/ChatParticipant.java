package com.delta.common.chat.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话参与者表实体，对应 chat_participant
 */
@Data
@TableName("chat_participant")
public class ChatParticipant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    private String userType;     // USER / PLAYER / CS / ADMIN

    private Long userId;

    private String roleInConv;   // USER / PLAYER / CS / MEMBER1 / MEMBER2

    private LocalDateTime lastReadAt;

    private Boolean isMuted;

    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String nickname;

    @TableField(exist = false)
    private String avatar;
}

