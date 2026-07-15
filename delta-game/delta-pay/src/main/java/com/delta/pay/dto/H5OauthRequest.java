package com.delta.pay.dto;

import lombok.Data;

/** POST /pay/h5/oauth 请求体 */
@Data
public class H5OauthRequest {
    private String code;
}
