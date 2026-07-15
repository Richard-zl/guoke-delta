package com.delta.common.chat.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.common.chat.domain.entity.ChatSession;
import com.delta.common.chat.util.ChatParticipantId;
import com.delta.common.constant.Constants;
import com.delta.common.exception.BusinessException;
import com.delta.common.mapper.CrossModuleMapper;
import com.delta.common.redis.service.RedisService;
import com.delta.common.sms.service.AliyunSmsService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSmsReminderService {
    private static final String CFG_ENABLED = "sms.aliyun.enabled";
    private static final String CFG_ACCESS_KEY_ID = "sms.aliyun.access_key_id";
    private static final String CFG_ACCESS_KEY_SECRET = "sms.aliyun.access_key_secret";
    private static final String CFG_ENDPOINT = "sms.aliyun.endpoint";
    private static final String CFG_SIGN_NAME = "sms.aliyun.sign_name";
    private static final String CFG_COOLDOWN_SECONDS = "sms.aliyun.cooldown_seconds";
    private static final String CFG_USER_PLAYER_COOLDOWN_SECONDS = "sms.aliyun.user_player_cooldown_seconds";
    private static final String CFG_PLAYER_FEE = "sms.aliyun.player_fee";
    private static final String DEFAULT_ENDPOINT = "dysmsapi.aliyuncs.com";
    private static final int DEFAULT_COOLDOWN_SECONDS = 60;
    private static final int DEFAULT_USER_PLAYER_COOLDOWN_SECONDS = 600;
    private static final BigDecimal DEFAULT_PLAYER_FEE = new BigDecimal("0.05");

    private final ChatServiceImpl chatService;
    private final CrossModuleMapper crossModuleMapper;
    private final RedisService redisService;
    private final AliyunSmsService aliyunSmsService;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(rollbackFor = Exception.class)
    public void sendReminder(Long sessionId, long myEncodedId, String reminderCode) {
        if (sessionId == null) {
            throw new BusinessException(400, "会话不存在");
        }
        ReminderType reminderType = ReminderType.fromCode(reminderCode);
        if (reminderType == null) {
            throw new BusinessException(400, "提醒类型不存在");
        }

        ChatSession session = chatService.getOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getId, sessionId)
                .eq(ChatSession::getStatus, "ACTIVE"), false);
        if (session == null) {
            throw new BusinessException(404, "会话不存在");
        }
        if (!isSessionMember(session, myEncodedId)) {
            throw new BusinessException(403, "无权限操作该会话");
        }

        long targetEncodedId = session.getId1() != null && session.getId1().equals(myEncodedId)
                ? session.getId2() : session.getId1();
        String senderType = normalizeParticipantType(myEncodedId);
        String targetType = normalizeParticipantType(targetEncodedId);
        if (!reminderType.supports(senderType, targetType)) {
            throw new BusinessException(400, "当前会话不支持该短信提醒");
        }

        if (!"true".equalsIgnoreCase(getConfig(CFG_ENABLED, "false"))) {
            throw new BusinessException(400, "短信提醒未开启");
        }

        String accessKeyId = getConfig(CFG_ACCESS_KEY_ID, "");
        String accessKeySecret = getConfig(CFG_ACCESS_KEY_SECRET, "");
        String endpoint = getConfig(CFG_ENDPOINT, DEFAULT_ENDPOINT);
        String signName = getConfig(CFG_SIGN_NAME, "");
        String templateCode = getConfig(reminderType.getTemplateConfigKey(), reminderType.getDefaultTemplateCode());
        if (StrUtil.hasBlank(accessKeyId, accessKeySecret, endpoint, signName, templateCode)) {
            throw new BusinessException(400, "短信配置不完整，请先在系统配置中填写阿里云短信参数");
        }

        String phone = crossModuleMapper.selectPhoneByEncodedId(targetEncodedId);
        if (StrUtil.isBlank(phone)) {
            throw new BusinessException(400, "对方未绑定手机号，无法发送短信提醒");
        }

        int cooldownSeconds = resolveCooldownSeconds(senderType);
        String cooldownKey = buildCooldownKey(senderType, myEncodedId);
        if (!redisService.tryLock(cooldownKey, Math.max(cooldownSeconds, 10), TimeUnit.SECONDS)) {
            if ("USER".equals(senderType) || "PLAYER".equals(senderType)) {
                throw new BusinessException(400, "每10分钟只能发送一次短信提醒");
            }
            throw new BusinessException(400, "发送过于频繁，请稍后再试");
        }

        boolean smsSent = false;
        String walletLockKey = null;
        try {
            BigDecimal playerFee = resolvePlayerFee(senderType);
            if ("PLAYER".equals(senderType) && playerFee.compareTo(BigDecimal.ZERO) > 0) {
                Long playerId = ChatParticipantId.getRawId(myEncodedId);
                walletLockKey = Constants.LOCK_WALLET + "player:" + playerId;
                if (!redisService.tryLock(walletLockKey, 10, TimeUnit.SECONDS)) {
                    throw new BusinessException(400, "操作太频繁，请稍后重试");
                }
                chargePlayerSmsFee(playerId, playerFee);
            }
            aliyunSmsService.sendTemplate(endpoint, accessKeyId, accessKeySecret, signName, templateCode, phone,
                    Map.of());
            smsSent = true;
        } catch (RuntimeException e) {
            if (!smsSent) {
                redisService.unlock(cooldownKey);
            }
            throw e;
        } finally {
            if (walletLockKey != null) {
                redisService.unlock(walletLockKey);
            }
        }
        log.info("chat sms reminder sent, sessionId={}, senderType={}, targetType={}, reminderCode={}",
                sessionId, senderType, targetType, reminderType.getCode());
    }

    private String getConfig(String key, String defaultValue) {
        String value = crossModuleMapper.selectConfigValue(key);
        return StrUtil.isBlank(value) ? defaultValue : value.trim();
    }

    private boolean isSessionMember(ChatSession session, long myEncodedId) {
        return session != null && ((session.getId1() != null && session.getId1().equals(myEncodedId))
                || (session.getId2() != null && session.getId2().equals(myEncodedId)));
    }

    private String normalizeParticipantType(long encodedId) {
        return switch (ChatParticipantId.getEntityType(encodedId)) {
            case 2 -> "PLAYER";
            case 3 -> "CS";
            default -> "USER";
        };
    }

    private int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private int resolveCooldownSeconds(String senderType) {
        if ("USER".equals(senderType) || "PLAYER".equals(senderType)) {
            return parseInt(getConfig(CFG_USER_PLAYER_COOLDOWN_SECONDS, String.valueOf(DEFAULT_USER_PLAYER_COOLDOWN_SECONDS)),
                    DEFAULT_USER_PLAYER_COOLDOWN_SECONDS);
        }
        return parseInt(getConfig(CFG_COOLDOWN_SECONDS, String.valueOf(DEFAULT_COOLDOWN_SECONDS)), DEFAULT_COOLDOWN_SECONDS);
    }

    private String buildCooldownKey(String senderType, long myEncodedId) {
        long rawId = ChatParticipantId.getRawId(myEncodedId);
        return "chat:sms:reminder:cooldown:" + senderType + ":" + rawId;
    }

    private BigDecimal resolvePlayerFee(String senderType) {
        if (!"PLAYER".equals(senderType)) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal fee = new BigDecimal(getConfig(CFG_PLAYER_FEE, DEFAULT_PLAYER_FEE.toPlainString()));
            if (fee.compareTo(BigDecimal.ZERO) < 0) {
                return BigDecimal.ZERO;
            }
            return fee.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception ignored) {
            return DEFAULT_PLAYER_FEE;
        }
    }

    private void chargePlayerSmsFee(Long playerId, BigDecimal fee) {
        Map<String, Object> row = jdbcTemplate.queryForList(
                "SELECT id, balance FROM player_wallet WHERE player_id = ? LIMIT 1", playerId)
                .stream()
                .findFirst()
                .orElse(null);
        if (row == null) {
            throw new BusinessException(400, "钱包不存在，无法发送短信提醒");
        }

        Long walletId = ((Number) row.get("id")).longValue();
        BigDecimal balanceBefore = toBigDecimal(row.get("balance"));
        if (balanceBefore.compareTo(fee) < 0) {
            throw new BusinessException(400, "余额不足，无法发送短信提醒");
        }
        BigDecimal balanceAfter = balanceBefore.subtract(fee).setScale(2, RoundingMode.HALF_UP);

        jdbcTemplate.update("UPDATE player_wallet SET balance = ?, updated_at = NOW() WHERE id = ?",
                balanceAfter, walletId);
        jdbcTemplate.update(
                "INSERT INTO transaction(type, user_type, user_id, amount, balance_before, balance_after, related_order_id, related_payment_id, related_withdraw_id, remark, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                "SMS_REMINDER",
                "PLAYER",
                playerId,
                fee.negate(),
                balanceBefore,
                balanceAfter,
                null,
                null,
                null,
                "聊天短信提醒扣费"
        );
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(String.valueOf(value));
    }

    @Getter
    @RequiredArgsConstructor
    private enum ReminderType {
        PLAYER_FINISH_ORDER("PLAYER_FINISH_ORDER", Set.of("PLAYER"), Set.of("USER"),
                "sms.aliyun.template_code.player_finish_order", "SMS_504575043"),
        CS_MESSAGE_REMINDER("CS_MESSAGE_REMINDER", Set.of("CS", "USER", "PLAYER"), Set.of("USER", "PLAYER"),
                "sms.aliyun.template_code.cs_message_reminder", "SMS_504365043");

        private final String code;
        private final Set<String> senderTypes;
        private final Set<String> targetTypes;
        private final String templateConfigKey;
        private final String defaultTemplateCode;

        boolean supports(String senderType, String targetType) {
            return senderTypes.contains(senderType) && targetTypes.contains(targetType);
        }

        static ReminderType fromCode(String code) {
            if (StrUtil.isBlank(code)) {
                return null;
            }
            for (ReminderType value : values()) {
                if (value.code.equalsIgnoreCase(code)) {
                    return value;
                }
            }
            return null;
        }
    }
}
