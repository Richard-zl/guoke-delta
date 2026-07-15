package com.delta.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private int code;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
