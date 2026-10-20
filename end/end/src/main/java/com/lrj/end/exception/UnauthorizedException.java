package com.lrj.end.exception;

/**
 * 未认证异常 → HTTP 401。触发场景：token 缺失/失效/闲置超时/账号被封禁。
 * 前端收到 401 统一清掉本地 token 并跳登录页。
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
