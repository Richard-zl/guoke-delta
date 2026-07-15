package com.delta.common.exception;

import com.delta.common.domain.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public R<?> handleBusiness(BusinessException e, HttpServletRequest req) {
        log.error("业务异常: {} URI: {}", e.getMessage(), req.getRequestURI());
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<?> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage() : "参数校验失败";
        return R.fail(400, msg);
    }

    @ExceptionHandler(BindException.class)
    public R<?> handleBind(BindException e) {
        String msg = e.getFieldError() != null ? e.getFieldError().getDefaultMessage() : "参数绑定失败";
        return R.fail(400, msg);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<?> handleMethod(HttpRequestMethodNotSupportedException e) {
        return R.fail(405, "请求方法不支持: " + e.getMethod());
    }

    @ExceptionHandler(Exception.class)
    public R<?> handleException(Exception e, HttpServletRequest req) {
        log.error("系统异常: {} URI: {}", e.getMessage(), req.getRequestURI(), e);
        String detail = e.getClass().getSimpleName() + ": " + e.getMessage();
        return R.fail(500, detail);
    }
}
