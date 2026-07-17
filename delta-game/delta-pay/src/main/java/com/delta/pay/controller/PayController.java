package com.delta.pay.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.common.enums.OrderStatusEnum;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.pay.config.WxPayConfiguration;
import com.delta.pay.entity.Payment;
import com.delta.pay.entity.Transaction;
import com.delta.pay.service.PayTokenService;
import com.delta.pay.service.PaymentService;
import com.delta.pay.service.TransactionService;
import com.delta.pay.service.WxJsapiPayHelper;
import com.delta.pay.wxkf.WxKfService;
import com.delta.order.entity.Order;
import com.delta.order.service.OrderService;
import com.delta.user.entity.User;
import com.delta.user.service.UserService;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.*;
import com.wechat.pay.java.service.payments.model.Transaction.TradeStateEnum;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/pay")
@RequiredArgsConstructor
public class PayController {
    private final PaymentService paymentService;
    private final TransactionService transactionService;
    private final OrderService orderService;
    private final PayTokenService payTokenService;
    private final WxKfService wxKfService;
    private final ObjectProvider<JsapiServiceExtension> jsapiServiceExtensionProvider;
    private final ObjectProvider<WxPayConfiguration> wxPayConfigurationProvider;
    private final UserService userService;
    private final WxJsapiPayHelper wxJsapiPayHelper;

    @Value("${pay.kf.token-ttl-seconds:900}")
    private long payTokenTtlSeconds;

    @PostMapping("/wx/{orderId}")
    public R<Map<String, String>> wxPay(@PathVariable Long orderId) {
        JsapiServiceExtension jsapiServiceExtension = jsapiServiceExtensionProvider.getIfAvailable();
        WxPayConfiguration wxPayConfiguration = wxPayConfigurationProvider.getIfAvailable();
        if (jsapiServiceExtension == null || wxPayConfiguration == null) {
            return R.fail("微信支付未启用，请使用余额支付或联系管理员配置支付证书");
        }

        Long userId = SecurityUtils.getUserId();
        Payment payment = paymentService.createWxPayment(orderId, userId);

        User user = userService.getById(userId);
        Order order = orderService.getById(orderId);
        String description = (order != null && order.getProductName() != null && !order.getProductName().isEmpty())
                ? order.getProductName() : "订单支付";
        try {
            PrepayWithRequestPaymentResponse response = wxJsapiPayHelper.createPrepayOrder(jsapiServiceExtension, wxPayConfiguration,
                    payment, wxPayConfiguration.getAppId(), user.getOpenid(), description);

            payment.setWxPrepayId(response.getPackageVal().replace("prepay_id=", ""));
            payment.setPayChannel("MINIAPP");
            paymentService.updateById(payment);

            Map<String, String> payParams = new HashMap<>();
            payParams.put("timeStamp", response.getTimeStamp());
            payParams.put("nonceStr", response.getNonceStr());
            payParams.put("package", response.getPackageVal());
            payParams.put("signType", response.getSignType());
            payParams.put("paySign", response.getPaySign());
            return R.ok(payParams);
        } catch (ServiceException e) {
            log.error("微信支付V3下单失败, orderId={}, code={}", orderId, e.getErrorCode(), e);
            if ("ORDERPAID".equals(e.getErrorCode())) {
                return R.fail(4010, "该订单已支付，请勿重复支付");
            }
            return R.fail("发起微信支付失败，请稍后重试");
        } catch (Exception e) {
            log.error("微信支付V3下单失败, orderId={}", orderId, e);
            return R.fail("发起微信支付失败，请稍后重试");
        }
    }

    @PostMapping("/balance/{orderId}")
    public R<Payment> balancePay(@PathVariable Long orderId) {
        return R.ok(paymentService.createBalancePayment(orderId, SecurityUtils.getUserId()));
    }

    /** 打手入驻押金支付（金额来自 sys_config: player.deposit_amount），返回支付参数及paymentNo供提交申请时校验 */
    @PostMapping("/player-deposit")
    public R<Map<String, Object>> playerDeposit() {
        JsapiServiceExtension jsapiServiceExtension = jsapiServiceExtensionProvider.getIfAvailable();
        WxPayConfiguration wxPayConfiguration = wxPayConfigurationProvider.getIfAvailable();
        if (jsapiServiceExtension == null || wxPayConfiguration == null) {
            return R.fail("微信支付未启用，暂无法支付押金");
        }

        Long userId = SecurityUtils.getUserId();
        Payment payment = paymentService.createPlayerDepositPayment(userId);
        User user = userService.getById(userId);
        if (user == null || user.getOpenid() == null) {
            return R.fail("用户信息不完整，无法发起支付");
        }
        try {
            PrepayWithRequestPaymentResponse response = wxJsapiPayHelper.createPrepayOrder(jsapiServiceExtension, wxPayConfiguration,
                    payment, wxPayConfiguration.getAppId(), user.getOpenid(), "打手入驻押金");
            payment.setWxPrepayId(response.getPackageVal().replace("prepay_id=", ""));
            payment.setPayChannel("MINIAPP");
            paymentService.updateById(payment);
            Map<String, Object> result = new HashMap<>();
            result.put("paymentNo", payment.getPaymentNo());
            result.put("timeStamp", response.getTimeStamp());
            result.put("nonceStr", response.getNonceStr());
            result.put("package", response.getPackageVal());
            result.put("signType", response.getSignType());
            result.put("paySign", response.getPaySign());
            return R.ok(result);
        } catch (ServiceException e) {
            log.error("打手押金微信支付下单失败, userId={}, code={}", userId, e.getErrorCode(), e);
            return R.fail("发起微信支付失败，请稍后重试");
        } catch (Exception e) {
            log.error("打手押金微信支付下单失败, userId={}", userId, e);
            return R.fail("发起微信支付失败，请稍后重试");
        }
    }

    @PostMapping("/wx/notify")
    public Map<String, String> wxNotify(HttpServletRequest httpRequest, @RequestBody String jsonData) {
        Map<String, String> response = new HashMap<>();
        try {
            String serial = httpRequest.getHeader("Wechatpay-Serial");
            String nonce = httpRequest.getHeader("Wechatpay-Nonce");
            String timestamp = httpRequest.getHeader("Wechatpay-Timestamp");
            String signature = httpRequest.getHeader("Wechatpay-Signature");

            log.info("收到微信支付V3回调, serial={}", serial);

            paymentService.handleWxPayV3Notify(jsonData, serial, nonce, timestamp, signature);

            response.put("code", "SUCCESS");
            response.put("message", "成功");
        } catch (Exception e) {
            log.error("处理微信支付V3回调失败", e);
            response.put("code", "FAIL");
            response.put("message", "处理失败");
        }
        return response;
    }

    @GetMapping("/transactions")
    public R<Page<Transaction>> transactions(PageQuery query) {
        Long userId = SecurityUtils.getUserId();
        return R.ok(transactionService.page(new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<Transaction>().eq(Transaction::getUserId, userId)
                        .orderByDesc(Transaction::getCreatedAt)));
    }

    /** 签发客服会话 H5 支付链路使用的短时效 payToken，供小程序拉起「联系客服」时携带 */
    @GetMapping("/kf/token/{orderId}")
    public R<Map<String, Object>> issueKfPayToken(@PathVariable Long orderId) {
        Long userId = SecurityUtils.getUserId();
        Order order = orderService.getById(orderId);
        if (order == null) {
            return R.fail("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            return R.fail("无权操作");
        }
        if (!OrderStatusEnum.PENDING_PAYMENT.name().equals(order.getStatus())) {
            return R.fail("订单状态不允许支付");
        }
        String token = payTokenService.issue(orderId, userId);
        // 必须用 add_contact_way(scene=pay) 生成的链接，scene_param 才会在 enter_session 回传
        String serviceUrl = wxKfService.buildPayServiceUrl(token);
        log.info("签发客服支付凭证 orderId={}, serviceUrlHasEncScene={}, urlPreview={}",
                orderId,
                serviceUrl.contains("enc_scene=") || serviceUrl.contains("encScene="),
                serviceUrl.replaceAll("scene_param=[^&]*", "scene_param=***"));
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("serviceUrl", serviceUrl);
        data.put("expireSeconds", payTokenTtlSeconds);
        return R.ok(data);
    }
}
