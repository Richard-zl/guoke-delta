package com.delta.user.dto;

import lombok.Data;

@Data
public class WxLoginRequest {
    private String code;
    /** 微信获取手机号的code */
    private String phoneCode;
    /** 首次注册可选：用户填写的昵称 */
    private String nickname;
    /** 首次注册可选：用户上传后的头像 URL */
    private String avatar;
}
