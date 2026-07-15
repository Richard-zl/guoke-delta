package com.delta.pay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.common.enums.OrderStatusEnum;
import com.delta.common.event.BusinessEvent;
import com.delta.common.exception.BusinessException;
import com.delta.common.service.CouponService;
import com.delta.common.utils.IdGenerator;
import com.delta.order.entity.Order;
import com.delta.order.service.OrderService;
import com.delta.pay.entity.Payment;
import com.delta.pay.mapper.PaymentMapper;
import com.delta.pay.service.PaymentService;
import com.delta.pay.service.TransactionService;
import com.delta.order.service.OrderProgressService;
import com.delta.product.entity.Product;
import com.delta.product.service.ProductService;
import com.delta.product.service.TrialOrderService;
import com.delta.system.service.SysConfigService;
import com.delta.user.entity.Wallet;
import com.delta.user.service.WalletService;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl extends ServiceImpl<PaymentMapper, Payment> implements PaymentService {
    private final TransactionService transactionService;
    private final OrderService orderService;
    private final OrderProgressService orderProgressService;
    private final WalletService walletService;
    private final ProductService productService;
    private final SysConfigService sysConfigService;
    private final ObjectProvider<NotificationParser> notificationParserProvider;
    private final ObjectProvider<RefundService> refundServiceProvider;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final CouponService couponService;
    private final TrialOrderService trialOrderService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Payment createWxPayment(Long orderId, Long userId) {
        Order order = orderService.getById(orderId);
        if (order == null) throw new BusinessException("订单不存在");
        if (!OrderStatusEnum.PENDING_PAYMENT.name().equals(order.getStatus()))
            throw new BusinessException("订单状态不允许支付");
        if (!order.getUserId().equals(userId)) throw new BusinessException("无权操作");
        if (order.getPayDeadline() != null && order.getPayDeadline().isBefore(LocalDateTime.now()))
            throw new BusinessException("订单已超时，请重新下单");

        validateTrialOrderPayment(order, userId, orderId);

        Payment paidPayment = getOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, orderId)
                .eq(Payment::getStatus, "PAID")
                .last("LIMIT 1"));
        if (paidPayment != null) {
            throw new BusinessException(4010, "该订单已支付，请勿重复支付");
        }

        Payment existingPayment = getOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, orderId)
                .eq(Payment::getStatus, "PAYING"));
        if (existingPayment != null) {
            return existingPayment;
        }

        Payment payment = new Payment();
        payment.setPaymentNo(IdGenerator.nextPaymentNo());
        payment.setOrderId(orderId);
        payment.setUserId(userId);
        payment.setBizType("ORDER");
        payment.setAmount(order.getAmount());
        payment.setPayMethod("WECHAT");
        payment.setStatus("PAYING");
        save(payment);
        return payment;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Payment createPlayerDepositPayment(Long userId) {
        BigDecimal amount = getPlayerDepositAmount();
        Payment existing = getOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getUserId, userId)
                .eq(Payment::getBizType, "PLAYER_DEPOSIT")
                .eq(Payment::getStatus, "PAID")
                .last("LIMIT 1"));
        if (existing != null) {
            throw new BusinessException("您已支付过押金，无需重复支付");
        }
        Payment paying = getOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getUserId, userId)
                .eq(Payment::getBizType, "PLAYER_DEPOSIT")
                .eq(Payment::getStatus, "PAYING")
                .last("LIMIT 1"));
        if (paying != null && amount.compareTo(paying.getAmount()) == 0) {
            return paying;
        }
        Payment payment = new Payment();
        payment.setPaymentNo(IdGenerator.nextPaymentNo());
        payment.setOrderId(null);
        payment.setUserId(userId);
        payment.setBizType("PLAYER_DEPOSIT");
        payment.setAmount(amount);
        payment.setPayMethod("WECHAT");
        payment.setStatus("PAYING");
        save(payment);
        return payment;
    }

    @Override
    public boolean verifyPlayerDepositPayment(String paymentNo, Long userId) {
        if (paymentNo == null || paymentNo.isBlank()) return false;
        BigDecimal expectedAmount = getPlayerDepositAmount();
        Payment payment = getOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getPaymentNo, paymentNo));
        if (payment == null) return false;
        if (!"PLAYER_DEPOSIT".equals(payment.getBizType())) return false;
        if (!"PAID".equals(payment.getStatus())) return false;
        if (!userId.equals(payment.getUserId())) return false;
        if (payment.getAmount().compareTo(expectedAmount) != 0) return false;
        return true;
    }

    @Override
    public Payment createBalancePayment(Long orderId, Long userId) {
        Order order = orderService.getById(orderId);
        if (order == null) throw new BusinessException("订单不存在");
        if (!OrderStatusEnum.PENDING_PAYMENT.name().equals(order.getStatus()))
            throw new BusinessException("订单状态不允许支付");
        if (!order.getUserId().equals(userId)) throw new BusinessException("无权操作");
        if (order.getPayDeadline() != null && order.getPayDeadline().isBefore(LocalDateTime.now()))
            throw new BusinessException("订单已超时，请重新下单");

        validateTrialOrderPayment(order, userId, orderId);

        String lockKey = "wallet:" + userId;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1",
                java.time.Duration.ofSeconds(10));
        if (Boolean.FALSE.equals(locked)) throw new BusinessException("操作太频繁，请稍后重试");
        try {
            return doBalancePay(orderId, userId, order);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Payment doBalancePay(Long orderId, Long userId, Order order) {
        Wallet wallet = walletService.getByUserId(userId);
        if (wallet == null) throw new BusinessException("钱包不存在");
        BigDecimal available = wallet.getBalance().subtract(wallet.getFrozenAmount());
        if (available.compareTo(order.getAmount()) < 0) {
            throw new BusinessException("余额不足");
        }

        BigDecimal balanceBefore = wallet.getBalance();
        wallet.setBalance(wallet.getBalance().subtract(order.getAmount()));
        walletService.updateById(wallet);

        Payment payment = new Payment();
        payment.setPaymentNo(IdGenerator.nextPaymentNo());
        payment.setOrderId(orderId);
        payment.setUserId(userId);
        payment.setBizType("ORDER");
        payment.setAmount(order.getAmount());
        payment.setPayMethod("BALANCE");
        payment.setStatus("PAID");
        payment.setPaidAt(LocalDateTime.now());
        save(payment);

        transactionService.record("CONSUMPTION", "USER", userId, order.getAmount().negate(),
                balanceBefore, wallet.getBalance(), orderId, payment.getId(), null, "余额支付");

        String fromStatus = order.getStatus();
        order.setStatus(OrderStatusEnum.PAID.name());
        order.setUpdatedAt(LocalDateTime.now());
        orderService.updateById(order);
        recordProgress(orderId, fromStatus, OrderStatusEnum.PAID.name(), "USER", userId, "余额支付成功");

        incrementSalesCount(order.getProductId());

        // 核销优惠券
        couponService.markCouponUsed(order.getUserCouponId(), orderId);

        // 支付成功，删除Redis超时取消key
        redisTemplate.delete("order:pay_timeout:" + orderId);

        eventPublisher.publishEvent(new BusinessEvent(this, "ORDER_PAID",
                "USER", userId, orderId, "订单支付成功，等待打手接单"));

        // 若下单时指定了打手，支付成功后自动指派
        if (order.getDesignatedPlayerId() != null) {
            try {
                orderService.assignOrder(orderId, order.getDesignatedPlayerId());
            } catch (Exception e) {
                log.warn("支付成功后自动指派失败 orderId={} playerId={}", orderId, order.getDesignatedPlayerId(), e);
            }
        }

        return payment;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleWxPayV3Notify(String jsonData, String serial, String nonce, String timestamp, String signature) {
        log.info("处理微信支付V3回调");
        NotificationParser notificationParser = notificationParserProvider.getIfAvailable();
        if (notificationParser == null) {
            throw new BusinessException("微信支付未启用，无法处理支付回调");
        }
        try {
            RequestParam requestParam = new RequestParam.Builder()
                    .serialNumber(serial)
                    .nonce(nonce)
                    .signature(signature)
                    .timestamp(timestamp)
                    .body(jsonData)
                    .build();

            Transaction transaction = notificationParser.parse(requestParam, Transaction.class);

            String outTradeNo = transaction.getOutTradeNo();
            String transactionId = transaction.getTransactionId();
            Transaction.TradeStateEnum tradeState = transaction.getTradeState();

            log.info("V3回调解密: outTradeNo={}, transactionId={}, tradeState={}", outTradeNo, transactionId, tradeState);

            if (tradeState != Transaction.TradeStateEnum.SUCCESS) {
                log.warn("支付未成功, tradeState={}", tradeState);
                return;
            }

            Payment payment = getOne(new LambdaQueryWrapper<Payment>()
                    .eq(Payment::getPaymentNo, outTradeNo));
            if (payment == null || "PAID".equals(payment.getStatus())) return;

            Order order = payment.getOrderId() != null ? orderService.getById(payment.getOrderId()) : null;
            if (order != null && OrderStatusEnum.PENDING_PAYMENT.name().equals(order.getStatus())) {
                validateTrialOrderPayment(order, order.getUserId(), order.getId());
            }

            payment.setStatus("PAID");
            payment.setWxTransactionId(transactionId);
            payment.setPaidAt(LocalDateTime.now());
            updateById(payment);

            if (order != null && OrderStatusEnum.PENDING_PAYMENT.name().equals(order.getStatus())) {
                String fromStatus = order.getStatus();
                order.setStatus(OrderStatusEnum.PAID.name());
                order.setUpdatedAt(LocalDateTime.now());
                orderService.updateById(order);
                recordProgress(order.getId(), fromStatus, OrderStatusEnum.PAID.name(),
                        "SYSTEM", null, "微信支付成功");
                incrementSalesCount(order.getProductId());

                // 核销优惠券
                couponService.markCouponUsed(order.getUserCouponId(), order.getId());

                // 支付成功，删除Redis超时取消key
                redisTemplate.delete("order:pay_timeout:" + order.getId());

                eventPublisher.publishEvent(new BusinessEvent(this, "ORDER_PAID",
                        "USER", order.getUserId(), order.getId(), "订单支付成功，等待打手接单"));

                // 若下单时指定了打手，支付成功后自动指派
                if (order.getDesignatedPlayerId() != null) {
                    try {
                        orderService.assignOrder(order.getId(), order.getDesignatedPlayerId());
                    } catch (Exception ex) {
                        log.warn("支付成功后自动指派失败 orderId={} playerId={}", order.getId(), order.getDesignatedPlayerId(), ex);
                    }
                }
            }
        } catch (Exception e) {
            log.error("处理微信支付V3回调失败", e);
            throw new BusinessException("回调处理失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refund(Long paymentId, BigDecimal refundAmount, String reason) {
        Payment payment = getById(paymentId);
        if (payment == null) throw new BusinessException("支付记录不存在");
        if (!"PAID".equals(payment.getStatus())) throw new BusinessException("该支付记录不可退款");
        if (refundAmount.compareTo(payment.getAmount()) > 0) throw new BusinessException("退款金额不可超过支付金额");

        processRefund(payment, refundAmount, reason);

        Order order = orderService.getById(payment.getOrderId());
        String fromStatus = order.getStatus();
        order.setStatus(OrderStatusEnum.REFUNDING.name());
        order.setUpdatedAt(LocalDateTime.now());
        orderService.updateById(order);
        recordProgress(order.getId(), fromStatus, OrderStatusEnum.REFUNDING.name(), "SYSTEM", null, "退款处理中: " + reason);

        order.setStatus(OrderStatusEnum.REFUNDED.name());
        order.setUpdatedAt(LocalDateTime.now());
        orderService.updateById(order);
        recordProgress(order.getId(), OrderStatusEnum.REFUNDING.name(), OrderStatusEnum.REFUNDED.name(), "SYSTEM", null, "退款完成: " + reason);
        couponService.restoreCouponOnRefund(order.getUserCouponId(), order.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundByOrderId(Long orderId, String reason) {
        Payment payment = getOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, orderId)
                .eq(Payment::getStatus, "PAID")
                .last("LIMIT 1"));
        if (payment == null) {
            log.warn("退款跳过: 未找到已支付记录, orderId={}", orderId);
            return;
        }

        processRefund(payment, payment.getAmount(), reason);

        Order order = orderService.getById(orderId);
        if (order != null) {
            String fromStatus = order.getStatus();
            order.setStatus(OrderStatusEnum.REFUNDED.name());
            order.setUpdatedAt(LocalDateTime.now());
            orderService.updateById(order);
            recordProgress(orderId, fromStatus, OrderStatusEnum.REFUNDED.name(), "SYSTEM", null, "退款完成: " + reason);
            eventPublisher.publishEvent(new BusinessEvent(this, "ORDER_REFUNDED", "USER", order.getUserId(), orderId, "订单已退款"));
            couponService.restoreCouponOnRefund(order.getUserCouponId(), orderId);
        }
    }

    private void processRefund(Payment payment, BigDecimal refundAmount, String reason) {
        payment.setRefundNo(IdGenerator.nextPaymentNo());
        payment.setRefundAmount(refundAmount);
        payment.setRefundReason(reason);
        payment.setRefundTime(LocalDateTime.now());

        if ("BALANCE".equals(payment.getPayMethod())) {
            Wallet wallet = walletService.getByUserId(payment.getUserId());
            BigDecimal balanceBefore = wallet.getBalance();
            wallet.setBalance(wallet.getBalance().add(refundAmount));
            walletService.updateById(wallet);
            transactionService.record("REFUND", "USER", payment.getUserId(), refundAmount,
                    balanceBefore, wallet.getBalance(), payment.getOrderId(), payment.getId(), null, "退款: " + reason);
            log.info("余额退款完成: orderId={}, amount={}", payment.getOrderId(), refundAmount);
        } else if ("WECHAT".equals(payment.getPayMethod())) {
            RefundService refundService = refundServiceProvider.getIfAvailable();
            if (refundService == null) {
                throw new BusinessException("微信支付未启用，无法发起微信退款");
            }
            try {
                CreateRequest refundRequest = new CreateRequest();
                refundRequest.setOutTradeNo(payment.getPaymentNo());
                refundRequest.setOutRefundNo(payment.getRefundNo());
                refundRequest.setReason(reason);

                AmountReq amountReq = new AmountReq();
                amountReq.setRefund(refundAmount.multiply(new BigDecimal("100")).longValue());
                amountReq.setTotal(payment.getAmount().multiply(new BigDecimal("100")).longValue());
                amountReq.setCurrency("CNY");
                refundRequest.setAmount(amountReq);

                refundService.create(refundRequest);
                log.info("微信退款请求已提交: orderId={}, refundNo={}", payment.getOrderId(), payment.getRefundNo());
            } catch (Exception e) {
                log.error("微信退款请求失败: orderId={}", payment.getOrderId(), e);
                throw new BusinessException("微信退款请求失败");
            }
        }

        payment.setStatus("REFUNDED");
        updateById(payment);
    }

    private void validateTrialOrderPayment(Order order, Long userId, Long orderId) {
        if (order == null || order.getProductId() == null) {
            return;
        }
        Product product = productService.getById(order.getProductId());
        if (product != null) {
            trialOrderService.validateTrialOrderForPay(userId, orderId, product);
        }
    }

    private void incrementSalesCount(Long productId) {
        if (productId == null) return;
        Product product = productService.getById(productId);
        if (product != null) {
            product.setSalesCount((product.getSalesCount() == null ? 0 : product.getSalesCount()) + 1);
            productService.updateById(product);
        }
    }

    private void recordProgress(Long orderId, String from, String to, String operatorType, Long operatorId, String content) {
        com.delta.order.entity.OrderProgress progress = new com.delta.order.entity.OrderProgress();
        progress.setOrderId(orderId);
        progress.setType("STATUS_CHANGE");
        progress.setFromStatus(from);
        progress.setToStatus(to);
        progress.setOperatorType(operatorType);
        progress.setOperatorId(operatorId);
        progress.setContent(content);
        progress.setCreatedAt(LocalDateTime.now());
        orderProgressService.save(progress);
    }

    private BigDecimal getPlayerDepositAmount() {
        String amountStr = sysConfigService.getConfigValue("player.deposit_amount", "100");
        try {
            return new BigDecimal(amountStr);
        } catch (Exception e) {
            log.warn("打手押金金额配置无效，使用默认值100，config={}", amountStr);
            return new BigDecimal("100");
        }
    }
}
