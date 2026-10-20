package com.lrj.end.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记在 Controller 方法的 SysUser 参数上，由 CurrentUserArgumentResolver
 * 注入当前登录用户（AuthInterceptor 已校验并放入 request attribute）。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
