package com.delta.pay.domain;

/**
 * payToken 校验通过后解出的载荷。
 * 仅承载签发时写入的身份信息，不代表订单当前状态（订单状态由调用方另行查询）。
 */
public record PayTokenPayload(Long orderId, Long userId, long exp, String jti) {
}
