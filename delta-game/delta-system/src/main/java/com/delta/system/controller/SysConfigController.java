package com.delta.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Arrays;
import com.delta.common.annotation.OpLog;
import com.delta.common.domain.R;
import com.delta.system.entity.SysConfig;
import com.delta.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/system/config")
@RequiredArgsConstructor
public class SysConfigController {
    private final SysConfigService sysConfigService;

    @GetMapping("/list")
    public R<List<SysConfig>> list() {
        ensureBuiltinConfigs();
        return R.ok(sysConfigService.list(new LambdaQueryWrapper<SysConfig>().orderByAsc(SysConfig::getId)));
    }

    @PutMapping
    public R<Void> update(@RequestBody SysConfig config) {
        sysConfigService.updateById(config);
        return R.ok();
    }

    @OpLog(module = "system", operation = "修改系统配置")
    @PutMapping("/batch")
    public R<Void> batchUpdate(@RequestBody List<SysConfig> configs) {
        sysConfigService.updateBatchById(configs);
        return R.ok();
    }

    /** 公开接口：获取站点配置（无需登录） */
    @GetMapping("/site")
    public R<Map<String, String>> getSiteConfig(@RequestParam(required = false) String version) {
        ensureBuiltinConfigs();
        List<SysConfig> list = sysConfigService.list(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigGroup, "站点配置"));
        Map<String, String> map = list.stream()
                .collect(Collectors.toMap(SysConfig::getConfigKey, SysConfig::getConfigValue, (a, b) -> b));
        // 打手端抽佣比例（百分比）：从结算配置 settlement.commission_rate 转换，0.1 -> 10
        String commissionRateStr = sysConfigService.getConfigValue("settlement.commission_rate", "0.1");
        try {
            int percent = (int) Math.round(Double.parseDouble(commissionRateStr) * 100);
            map.put("player_commission_rate", String.valueOf(percent));
        } catch (NumberFormatException ignored) {
            map.put("player_commission_rate", "10");
        }
        // 打手押金开关
        map.put("player_deposit_required", sysConfigService.getConfigValue("player.deposit_required", "true"));
        // 打手押金金额（元）
        map.put("player_deposit_amount", sysConfigService.getConfigValue("player.deposit_amount", "100"));
        // 小程序审核版本：与客户端 versionName 精确匹配时视为审核中
        String auditVersion = sysConfigService.getConfigValue("app.audit_version", "");
        boolean underReview = auditVersion != null && !auditVersion.isBlank()
                && version != null && !version.isBlank()
                && auditVersion.equals(version);
        map.put("audit_version", auditVersion == null ? "" : auditVersion);
        map.put("is_under_review", String.valueOf(underReview));
        return R.ok(map);
    }

    private void ensureBuiltinConfigs() {
        ensureConfig("player.deposit_amount", "打手入驻押金金额", "100", "number", "打手配置",
                "开启打手押金时的应支付金额，单位：元");
        ensureConfig("sms.aliyun.enabled", "启用阿里云短信提醒", "false", "boolean", "短信配置",
                "开启后聊天页和后台业务事件均可发送短信提醒");
        ensureConfig("sms.aliyun.access_key_id", "阿里云 AccessKey ID", "", "text", "短信配置",
                "阿里云 RAM 用户的 AccessKey ID");
        ensureConfig("sms.aliyun.access_key_secret", "阿里云 AccessKey Secret", "", "text", "短信配置",
                "阿里云 RAM 用户的 AccessKey Secret");
        ensureConfig("sms.aliyun.endpoint", "阿里云短信 Endpoint", "dysmsapi.aliyuncs.com", "text", "短信配置",
                "短信服务默认 Endpoint，一般无需修改");
        ensureConfig("sms.aliyun.sign_name", "阿里云短信签名", "", "text", "短信配置",
                "已审核通过的短信签名");
        ensureConfig("sms.aliyun.template_code.cs_message_reminder", "消息提醒模板 Code", "SMS_504365043", "text", "短信配置",
                "用户、打手、客服端通用的消息提醒短信模板");
        ensureConfig("sms.aliyun.template_code.player_finish_order", "通知老板结单模板 Code", "SMS_504575043", "text", "短信配置",
                "打手端通知老板结单使用的固定内容短信模板");
        ensureConfig("sms.aliyun.template_code.player_order_assigned", "打手被指派模板 Code", "SMS_504890091", "text", "短信配置",
                "后台自动发送给被指派打手的固定内容短信模板");
        ensureConfig("sms.aliyun.template_code.player_teammate_invited", "打手被邀请模板 Code", "SMS_504775096", "text", "短信配置",
                "后台自动发送给被邀请打手的固定内容短信模板");
        ensureConfig("sms.aliyun.cooldown_seconds", "客服短信提醒冷却秒数", "60", "number", "短信配置",
                "客服发送短信提醒的冷却时间");
        ensureConfig("sms.aliyun.user_player_cooldown_seconds", "用户/打手短信冷却秒数", "600", "number", "短信配置",
                "用户端和打手端全局发送短信提醒冷却时间");
        ensureConfig("sms.aliyun.player_fee", "打手短信提醒扣费金额", "0.05", "number", "短信配置",
                "打手每次成功发送短信提醒时从余额中扣除的金额");
        ensureConfig("app.audit_version", "小程序审核版本号", "", "text", "小程序配置",
                "填写 versionName（如 1.0.1）；与该版本一致的小程序视为审核中，留空则关闭");
        ensureConfig("cs_contact_mode", "客服联系方式", "qrcode", "text", "站点配置",
                "qrcode=仅二维码；wework=仅API；auto=优先API失败降级二维码");
        ensureConfig("cs_corp_id", "企微企业ID", "", "text", "站点配置",
                "企业微信 corpId，小程序绑定企微客服后填写");
        ensureConfig("cs_service_url", "企微客服链接", "", "text", "站点配置",
                "微信客服 extInfo.url，绑定后从企微后台获取");
        ensureConfig("cs_qrcode_url", "客服二维码", "", "image", "站点配置",
                "企微「联系我」二维码图片 URL");
        ensureConfig("cs_contact_tips", "客服提示语", "长按识别二维码，添加客服微信", "text", "站点配置",
                "二维码弹窗底部说明文字");

        // 删除已废弃的配置项
        sysConfigService.remove(new LambdaQueryWrapper<SysConfig>()
                .in(SysConfig::getConfigKey, Arrays.asList(
                        "sms.aliyun.template_code.player_invite_login",
                        "sms.aliyun.template_code.user_allow_login",
                        "sms.aliyun.template_code.user_notify_player"
                )));
        // 将旧名称“客服消息提醒模板 Code”更新为“消息提醒模板 Code”
        SysConfig msgTpl = sysConfigService.getOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, "sms.aliyun.template_code.cs_message_reminder"));
        if (msgTpl != null && !"消息提醒模板 Code".equals(msgTpl.getConfigName())) {
            msgTpl.setConfigName("消息提醒模板 Code");
            msgTpl.setRemark("用户、打手、客服端通用的消息提醒短信模板");
            sysConfigService.updateById(msgTpl);
        }
    }

    private void ensureConfig(String key, String name, String value, String valueType, String group, String remark) {
        boolean exists = sysConfigService.exists(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key));
        if (exists) return;
        SysConfig config = new SysConfig();
        config.setConfigKey(key);
        config.setConfigName(name);
        config.setConfigValue(value);
        config.setValueType(valueType);
        config.setConfigGroup(group);
        config.setRemark(remark);
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        sysConfigService.save(config);
    }
}
