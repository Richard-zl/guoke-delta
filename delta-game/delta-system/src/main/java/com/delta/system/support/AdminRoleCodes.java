package com.delta.system.support;

/**
 * 后台账号角色与操作日志里的操作者类型互转。
 */
public final class AdminRoleCodes {
    private AdminRoleCodes() {}

    /** admin/cs/operator/manager/both → ADMIN/CS/OPERATOR/MANAGER */
    public static String toCode(String dbRole) {
        if (dbRole == null) {
            return "";
        }
        return switch (dbRole.trim().toLowerCase()) {
            case "admin", "both" -> "ADMIN";
            case "cs" -> "CS";
            case "operator" -> "OPERATOR";
            case "manager" -> "MANAGER";
            default -> "";
        };
    }

    /** ADMIN/CS/OPERATOR/MANAGER → 库里的 role */
    public static String toDbRole(String code) {
        if (code == null) {
            return "";
        }
        return switch (code.trim().toUpperCase()) {
            case "ADMIN" -> "admin";
            case "CS" -> "cs";
            case "OPERATOR" -> "operator";
            case "MANAGER" -> "manager";
            default -> "";
        };
    }
}
