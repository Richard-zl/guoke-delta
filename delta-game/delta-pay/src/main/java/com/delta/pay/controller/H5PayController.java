package com.delta.pay.controller;

import com.delta.common.domain.R;
import com.delta.common.dto.OrderCouponView;
import com.delta.common.enums.OrderStatusEnum;
import com.delta.common.exception.BusinessException;
import com.delta.common.service.CouponService;
import com.delta.order.entity.Order;
import com.delta.order.service.OrderService;
import com.delta.pay.config.WxPayConfiguration;
import com.delta.pay.domain.H5OrderView;
import com.delta.pay.domain.PayTokenPayload;
import com.delta.pay.dto.H5OauthRequest;
import com.delta.pay.dto.H5PrepayRequest;
import com.delta.pay.entity.Payment;
import com.delta.pay.service.MpJsapiSignService;
import com.delta.pay.service.MpOAuthService;
import com.delta.pay.service.PayTokenService;
import com.delta.pay.service.PaymentService;
import com.delta.pay.service.WxJsapiPayHelper;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 客服会话 H5 支付页后端接口：服务号 snsapi_base 授权 → 只读查单 → 服务号 JSAPI 预下单 → JSSDK 签名。
 * 均通过 payToken 鉴权（见 {@link PayTokenService}），不依赖登录态；已在 SecurityConfig 放行 /pay/h5/**。
 */
@Slf4j
@RestController
@RequestMapping("/pay/h5")
@RequiredArgsConstructor
public class H5PayController {

    private final PayTokenService payTokenService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final CouponService couponService;
    private final MpOAuthService mpOAuthService;
    private final MpJsapiSignService mpJsapiSignService;
    private final WxJsapiPayHelper wxJsapiPayHelper;
    private final ObjectProvider<JsapiServiceExtension> jsapiServiceExtensionProvider;
    private final ObjectProvider<WxPayConfiguration> wxPayConfigurationProvider;

    /**
     * 独立于受 wx.pay.enabled 约束的 {@link WxPayConfiguration}：H5 网页授权跳转（snsapi_base）
     * 只需要服务号 appid，不依赖支付证书是否配置，与 {@link MpOAuthService} 保持一致的读取方式。
     */
    @Value("${wx.mp.appid:}")
    private String mpAppId;

    /** 供 H5 页拼接微信网页授权跳转链接使用；未配置时返回空串，前端据此提示联系客服 */
    @GetMapping("/mp-appid")
    public R<Map<String, String>> mpAppId() {
        Map<String, String> data = new HashMap<>();
        data.put("appId", mpAppId == null ? "" : mpAppId);
        return R.ok(data);
    }

    @PostMapping("/oauth")
    public R<Map<String, String>> oauth(@RequestBody H5OauthRequest request) {
        String openid = mpOAuthService.codeToOpenid(request.getCode());
        Map<String, String> data = new HashMap<>();
        data.put("openid", openid);
        return R.ok(data);
    }

    @GetMapping("/order")
    public R<H5OrderView> order(@RequestParam String token) {
        PayTokenPayload payload = payTokenService.verify(token);
        Order order = requirePendingOrder(payload.orderId());

        OrderCouponView couponView = couponService.buildOrderCouponView(order.getUserCouponId(), order.getAmount());
        H5OrderView view = new H5OrderView(
                order.getId(),
                order.getOrderNo(),
                order.getProductName(),
                order.getAmount(),
                order.getStatus(),
                order.getPayDeadline(),
                couponView.getCouponName(),
                couponView.getCouponDiscountAmount());
        return R.ok(view);
    }

    @PostMapping("/prepay")
    public R<Map<String, String>> prepay(@RequestBody H5PrepayRequest request) {
        if (request.getOpenid() == null || request.getOpenid().isBlank()) {
            return R.fail("openid不能为空");
        }
        JsapiServiceExtension jsapiServiceExtension = jsapiServiceExtensionProvider.getIfAvailable();
        WxPayConfiguration wxPayConfiguration = wxPayConfigurationProvider.getIfAvailable();
        if (jsapiServiceExtension == null || wxPayConfiguration == null) {
            return R.fail("微信支付未启用，请联系客服");
        }
        String mpAppId = wxPayConfiguration.getMpAppId();
        if (mpAppId == null || mpAppId.isBlank()) {
            return R.fail("服务号未配置，暂无法完成支付，请联系客服");
        }

        PayTokenPayload payload = payTokenService.verify(request.getToken());
        Order order = requirePendingOrder(payload.orderId());

        Payment payment = paymentService.createWxPayment(order.getId(), payload.userId());
        String description = (order.getProductName() != null && !order.getProductName().isEmpty())
                ? order.getProductName() : "订单支付";
        try {
            PrepayWithRequestPaymentResponse response = wxJsapiPayHelper.createPrepayOrder(jsapiServiceExtension,
                    wxPayConfiguration, payment, mpAppId, request.getOpenid(), description);

            payment.setWxPrepayId(response.getPackageVal().replace("prepay_id=", ""));
            payment.setPayChannel("MP_H5");
            paymentService.updateById(payment);

            Map<String, String> payParams = new HashMap<>();
            payParams.put("timeStamp", response.getTimeStamp());
            payParams.put("nonceStr", response.getNonceStr());
            payParams.put("package", response.getPackageVal());
            payParams.put("signType", response.getSignType());
            payParams.put("paySign", response.getPaySign());
            return R.ok(payParams);
        } catch (ServiceException e) {
            log.error("H5服务号JSAPI下单失败, orderId={}, code={}", order.getId(), e.getErrorCode(), e);
            if ("ORDERPAID".equals(e.getErrorCode())) {
                return R.fail(4010, "该订单已支付，请勿重复支付");
            }
            return R.fail("发起微信支付失败，请稍后重试");
        } catch (Exception e) {
            log.error("H5服务号JSAPI下单失败, orderId={}", order.getId(), e);
            return R.fail("发起微信支付失败，请稍后重试");
        }
    }

    @GetMapping("/jsconfig")
    public R<Map<String, String>> jsconfig(@RequestParam String url) {
        return R.ok(mpJsapiSignService.sign(url));
    }

    /**
     * 校验订单存在且仍处于待支付状态——这是每次业务操作都必须重新核验的主防线（Spec §5.1），
     * payToken 本身不作废，全靠此处的订单状态兜底防止重复支付/滥用。
     */
    private Order requirePendingOrder(Long orderId) {
        Order order = orderService.getById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (OrderStatusEnum.PENDING_PAYMENT.name().equals(order.getStatus())) {
            return order;
        }
        if (OrderStatusEnum.PAID.name().equals(order.getStatus())) {
            throw new BusinessException(4010, "该订单已支付，请勿重复支付");
        }
        throw new BusinessException("订单状态已变更，请返回小程序查看");
    }
}
