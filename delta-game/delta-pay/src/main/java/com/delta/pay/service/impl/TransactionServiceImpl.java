package com.delta.pay.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.pay.entity.Transaction;
import com.delta.pay.mapper.TransactionMapper;
import com.delta.pay.service.TransactionService;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class TransactionServiceImpl extends ServiceImpl<TransactionMapper, Transaction> implements TransactionService {
    @Override
    public void record(String type, String userType, Long userId, BigDecimal amount,
                       BigDecimal balanceBefore, BigDecimal balanceAfter,
                       Long relatedOrderId, Long relatedPaymentId, Long relatedWithdrawId, String remark) {
        Transaction t = new Transaction();
        t.setType(type);
        t.setUserType(userType);
        t.setUserId(userId);
        t.setAmount(amount);
        t.setBalanceBefore(balanceBefore);
        t.setBalanceAfter(balanceAfter);
        t.setRelatedOrderId(relatedOrderId);
        t.setRelatedPaymentId(relatedPaymentId);
        t.setRelatedWithdrawId(relatedWithdrawId);
        t.setRemark(remark);
        t.setCreatedAt(LocalDateTime.now());
        save(t);
    }
}
