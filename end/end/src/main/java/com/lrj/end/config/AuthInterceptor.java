package com.lrj.end.config;

import com.lrj.end.entity.SysUser;
import com.lrj.end.exception.ForbiddenException;
import com.lrj.end.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证拦截器：保护 /api/** 下除注册、登录外的所有接口。
 *
 * 职责：
 *  1. 从 Authorization: Bearer <token> 头取令牌，交给 AuthService 校验
 *     （含闲置超时踢出、封禁踢出、滑动刷新）；
 *  2. 校验通过的用户存入 request attribute，供 @CurrentUser 注入；
 *  3. /api/admin/** 额外要求 admin 角色，否则 403。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    /** request attribute key，CurrentUserArgumentResolver 按它取用户 */
    public static final String CURRENT_USER_ATTRIBUTE = "currentUser";

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS 预检请求直接放行，由 CorsConfig 处理
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = extractToken(request);
        SysUser user = authService.authenticate(token);
        if (request.getRequestURI().startsWith("/api/admin/") && !"admin".equals(user.getRole())) {
            throw new ForbiddenException("需要管理员权限");
        }
        request.setAttribute(CURRENT_USER_ATTRIBUTE, user);
        return true;
    }

    /** 从 Authorization 头提取 Bearer 令牌；没有则返回 null，由 authenticate 统一抛 401 */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }
}
