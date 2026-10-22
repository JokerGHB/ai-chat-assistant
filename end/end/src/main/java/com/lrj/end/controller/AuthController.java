package com.lrj.end.controller;

import com.lrj.end.config.CurrentUser;
import com.lrj.end.dto.LoginRequest;
import com.lrj.end.dto.LoginResponse;
import com.lrj.end.dto.RegisterRequest;
import com.lrj.end.dto.UpdatePasswordRequest;
import com.lrj.end.dto.UpdateUsernameRequest;
import com.lrj.end.dto.UserProfileResponse;
import com.lrj.end.entity.SysUser;
import com.lrj.end.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。注册、登录在 WebConfig 中配置为不需要令牌，其余接口都需要。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 注册：用户名唯一，密码 6-12 位且含字母数字（DTO 层 @Pattern 校验） */
    @PostMapping("/register")
    public UserProfileResponse register(@Valid @RequestBody RegisterRequest request,
                                        HttpServletRequest http) {
        return authService.register(request.username(), request.password(), clientIp(http));
    }

    /** 登录：成功返回令牌 + 用户资料；封禁账号返回 403 */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request.username(), request.password(), clientIp(http));
    }

    /** 登出：删除服务端令牌，立即失效 */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        authService.logout(extractToken(http));
        return ResponseEntity.noContent().build();
    }

    /** 查看个人资料（当前登录用户由拦截器解析注入） */
    @GetMapping("/profile")
    public UserProfileResponse profile(@CurrentUser SysUser user) {
        return authService.profile(user.getId());
    }

    /** 修改密码：验证旧密码，成功后所有设备强制重新登录 */
    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(@CurrentUser SysUser user,
                                               @Valid @RequestBody UpdatePasswordRequest request,
                                               HttpServletRequest http) {
        authService.changePassword(user.getId(), request.oldPassword(), request.newPassword(), clientIp(http));
        return ResponseEntity.noContent().build();
    }

    /** 修改用户名：唯一性校验，成功后所有设备强制重新登录 */
    @PatchMapping("/username")
    public ResponseEntity<Void> changeUsername(@CurrentUser SysUser user,
                                               @Valid @RequestBody UpdateUsernameRequest request,
                                               HttpServletRequest http) {
        authService.changeUsername(user.getId(), request.username(), clientIp(http));
        return ResponseEntity.noContent().build();
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
    }

    /** 取客户端 IP 用于操作日志；本地直连场景直接用 remoteAddr */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
