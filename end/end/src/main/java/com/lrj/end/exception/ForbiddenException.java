package com.lrj.end.exception;

/**
 * 无权限异常 → HTTP 403。触发场景：已登录但角色不符（如普通用户访问 /api/admin/**）。
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
