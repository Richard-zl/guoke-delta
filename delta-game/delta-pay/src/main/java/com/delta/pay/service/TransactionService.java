package com.delta.pay.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.delta.pay.entity.Transaction;
public interface TransactionService extends IService<Transaction> {
    void record(String type, String userType, Long userId, java.math.BigDecimal amount,
                java.math.BigDecimal balanceBefore, java.math.BigDecimal balanceAfter,
                Long relatedOrderId, Long relatedPaymentId, Long relatedWithdrawId, String remark);
}
