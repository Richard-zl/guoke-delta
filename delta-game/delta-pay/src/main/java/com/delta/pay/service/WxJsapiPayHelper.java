package com.delta.pay.service;

import com.delta.pay.config.WxPayConfiguration;
import com.delta.pay.entity.Payment;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.Amount;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 微信支付 V3 JSAPI 预下单公共逻辑。appid/openid 均由调用方传入，
 * 因此同一套逻辑可分别服务于小程序（小程序 appid + 小程序 openid）
 * 与服务号 H5（服务号 appid + 服务号 openid）两条下单路径。
 */
@Component
public class WxJsapiPayHelper {

    public PrepayWithRequestPaymentResponse createPrepayOrder(JsapiServiceExtension jsapiServiceExtension,
                                                                WxPayConfiguration wxPayConfiguration,
                                                                Payment payment,
                                                                String appid,
                                                                String openid,
                                                                String description) {
        PrepayRequest request = new PrepayRequest();
        request.setAppid(appid);
        request.setMchid(wxPayConfiguration.getMchId());
        request.setDescription(description);
        request.setOutTradeNo(payment.getPaymentNo());
        request.setNotifyUrl(wxPayConfiguration.getNotifyUrl());

        Amount amount = new Amount();
        amount.setTotal(payment.getAmount().multiply(new BigDecimal("100")).intValue());
        amount.setCurrency("CNY");
        request.setAmount(amount);

        Payer payer = new Payer();
        payer.setOpenid(openid);
        request.setPayer(payer);

        return jsapiServiceExtension.prepayWithRequestPayment(request);
    }
}
