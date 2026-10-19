package com.delta.order.service;

import com.delta.common.exception.BusinessException;

import java.util.List;

/**
 * 客服同意退款时可选的原因。固定项原样保存，其他项保存为「其他：自定义内容」。
 */
public final class RefundApproveReasons {

    public static final List<String> FIXED = List.of(
            "无人接单",
            "下错订单",
            "更换订单",
            "客人原因",
            "投诉退款",
            "重复下单",
            "打手无法服务",
            "协商一致"
    );

    public static final String OTHER = "其他";
    private static final String OTHER_PREFIX = "其他：";
    private static final int CUSTOM_MAX = 100;

    private RefundApproveReasons() {
    }

    /**
     * 把客服选择整理成可入库的处理原因。
     * 未选择、其他项为空、或不是允许的原因时抛出业务异常。
     */
    public static String normalize(String remark) {
        String text = remark == null ? "" : remark.trim();
        if (text.isEmpty() || OTHER.equals(text)) {
            throw new BusinessException(text.isEmpty() ? "请选择退款原因" : "请填写其他原因");
        }
        if (FIXED.contains(text)) {
            return text;
        }
        if (text.startsWith(OTHER_PREFIX)) {
            String custom = text.substring(OTHER_PREFIX.length()).trim();
            if (custom.isEmpty()) {
                throw new BusinessException("请填写其他原因");
            }
            if (custom.length() > CUSTOM_MAX) {
                throw new BusinessException("其他原因最多100字");
            }
            return OTHER_PREFIX + custom;
        }
        throw new BusinessException("请选择退款原因");
    }
}
