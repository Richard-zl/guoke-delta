package com.delta.pay.dto;

import lombok.Data;

/**
 * POST /pay/h5/prepay 请求体。
 * 刻意不接收 userCouponId：H5 侧金额以下单时已绑定的优惠券为准，禁止在支付页改券（Spec 决议 #2）。
 */
@Data
public class H5PrepayRequest {
    private String token;
    private String openid;
}
