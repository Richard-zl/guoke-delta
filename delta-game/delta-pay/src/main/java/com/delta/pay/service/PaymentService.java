package com.delta.pay.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.delta.pay.entity.Payment;

public interface PaymentService extends IService<Payment> {
    Payment createWxPayment(Long orderId, Long userId);
    Payment createBalancePayment(Long orderId, Long userId);
    /** 创建打手入驻押金支付（金额来自 sys_config: player.deposit_amount），返回Payment含paymentNo */
    Payment createPlayerDepositPayment(Long userId);
    /** 校验打手押金支付是否有效（已支付、金额匹配当前配置、归属当前用户） */
    boolean verifyPlayerDepositPayment(String paymentNo, Long userId);
    void handleWxPayV3Notify(String jsonData, String serial, String nonce, String timestamp, String signature);
    void refund(Long paymentId, java.math.BigDecimal refundAmount, String reason);
    void refundByOrderId(Long orderId, String reason);
}
