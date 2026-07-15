package com.delta.user.dto;

import lombok.Data;

@Data
public class UserProfileVO {
    private Long id;
    private String nickname;
    private String avatar;
    private String phone;
    private java.math.BigDecimal balance;
    private Integer unreadMessageCount;
}
