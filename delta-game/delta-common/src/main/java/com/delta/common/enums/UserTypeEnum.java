package com.delta.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserTypeEnum {
    USER("USER", "用户"),
    PLAYER("PLAYER", "打手"),
    ADMIN("ADMIN", "管理员"),
    CS("CS", "客服");
    private final String code;
    private final String desc;
}
