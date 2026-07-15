package com.delta.pay.service;

import com.delta.pay.domain.PayTokenPayload;

/**
 * 客服会话 H5 支付链路使用的短时效 payToken 签发/校验服务。
 * token 为紧凑点分串：orderId.userId.exp.jti.sig，用于在企业微信客服跳转 H5
 * 支付页时携带订单归属信息，替代依赖 unionid 打通登录态。
 */
public interface PayTokenService {

    /**
     * 签发 payToken。
     *
     * @param orderId 订单ID
     * @param userId  下单用户ID
     * @return 紧凑格式 token
     */
    String issue(Long orderId, Long userId);

    /**
     * 校验 payToken 的签名与有效期，不校验订单当前状态（由调用方结合订单查询另行校验）。
     *
     * @param token 待校验 token
     * @return 校验通过后解出的载荷
     */
    PayTokenPayload verify(String token);
}
