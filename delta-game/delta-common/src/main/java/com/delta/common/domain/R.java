package com.delta.common.domain;

import lombok.Data;
import java.io.Serializable;

@Data
public class R<T> implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final int SUCCESS_CODE = 200;
    public static final int FAIL_CODE = 500;

    private int code;
    private String msg;
    private T data;

    public static <T> R<T> ok() { return restResult(null, SUCCESS_CODE, "操作成功"); }
    public static <T> R<T> ok(T data) { return restResult(data, SUCCESS_CODE, "操作成功"); }
    public static <T> R<T> ok(T data, String msg) { return restResult(data, SUCCESS_CODE, msg); }
    public static <T> R<T> fail() { return restResult(null, FAIL_CODE, "操作失败"); }
    public static <T> R<T> fail(String msg) { return restResult(null, FAIL_CODE, msg); }
    public static <T> R<T> fail(int code, String msg) { return restResult(null, code, msg); }

    private static <T> R<T> restResult(T data, int code, String msg) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setData(data);
        r.setMsg(msg);
        return r;
    }
}
