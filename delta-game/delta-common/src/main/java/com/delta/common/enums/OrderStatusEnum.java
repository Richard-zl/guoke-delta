package com.delta.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 代练/陪玩订单状态枚举
 *
 * 状态流转：
 *
 * —— 主流程 ——
 * PENDING_PAYMENT(待支付)
 *   └─ 用户支付 → PAID(待接单)
 *        ├─ 客服指派打手 → ASSIGNED(已指派)
 *        │    ├─ 打手接受 → ACCEPTED(已接单)
 *        │    └─ 打手拒绝 → PAID(待接单)
 *        └─ 打手直接接单 → ACCEPTED(已接单)
 *             ├─ 多人订单 → WAITING_TEAMMATE(组队中) → ACCEPTED
 *             └─ 打手开始服务 → IN_PROGRESS(进行中)
 *                  └─ 打手完成服务 → COMPLETED(待确认)
 *                       ├─ 用户确认 → CONFIRMED(已完成)
 *                       │    └─ 用户评价 → REVIEWED(已评价)
 *                       └─ 24h自动确认 → CONFIRMED(已完成)
 *
 * —— 取消/退款 ——
 * PENDING_PAYMENT → 用户取消/超时未付 → CANCELLED(已取消)
 * PAID            → 用户取消(未接单) → REFUNDING(退款中) → REFUNDED(已退款)
 *
 * —— 争议/仲裁 ——
 * IN_PROGRESS / COMPLETED → 用户投诉 → DISPUTED(争议中)
 * DISPUTED → 客服仲裁 → ARBITRATED(已仲裁)
 */
@Getter
@AllArgsConstructor
public enum OrderStatusEnum {

    /** 待支付 — 用户已下单，等待支付（30分钟内未付自动取消） */
    PENDING_PAYMENT("PENDING_PAYMENT", "待支付"),

    /** 待接单 — 用户已支付，等待打手接单或客服指派 */
    PAID("PAID", "待接单"),

    /** 已指派 — 客服已指派打手，等待打手确认接单 */
    ASSIGNED("ASSIGNED", "已指派"),

    /** 已接单 — 打手已接单，等待开始服务 */
    ACCEPTED("ACCEPTED", "已接单"),

    /** 组队中 — 多人订单，主打手已接单，等待队友加入 */
    WAITING_TEAMMATE("WAITING_TEAMMATE", "组队中"),

    /** 进行中 — 打手正在代练/陪玩服务中 */
    IN_PROGRESS("IN_PROGRESS", "进行中"),

    /** 待确认 — 打手已完成服务，等待用户确认（24h内未确认自动完成） */
    COMPLETED("COMPLETED", "待确认"),

    /** 已完成 — 用户已确认服务完成，触发打手结算 */
    CONFIRMED("CONFIRMED", "已完成"),

    /** 已评价 — 用户已对服务进行评价，订单结束 */
    REVIEWED("REVIEWED", "已评价"),

    /** 已取消 — 用户主动取消或超时未支付自动取消 */
    CANCELLED("CANCELLED", "已取消"),

    /** 退款中 — 退款申请已发起，等待到账 */
    REFUNDING("REFUNDING", "退款中"),

    /** 已退款 — 退款已到账 */
    REFUNDED("REFUNDED", "已退款"),

    /** 争议中 — 用户发起投诉，等待客服仲裁 */
    DISPUTED("DISPUTED", "争议中"),

    /** 已仲裁 — 客服已完成争议处理 */
    ARBITRATED("ARBITRATED", "已仲裁");

    private final String code;
    private final String desc;
}
