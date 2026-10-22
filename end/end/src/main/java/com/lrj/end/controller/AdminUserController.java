package com.lrj.end.controller;

import com.lrj.end.config.CurrentUser;
import com.lrj.end.dto.UserProfileResponse;
import com.lrj.end.entity.SysUser;
import com.lrj.end.service.AdminUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员用户管理接口（/api/admin/** 由 AuthInterceptor 校验 admin 角色）。
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /** 用户列表 */
    @GetMapping
    public List<UserProfileResponse> listUsers() {
        return adminUserService.listUsers();
    }

    /** 封禁：立即踢下线 */
    @PostMapping("/{userId}/ban")
    public ResponseEntity<Void> banUser(@CurrentUser SysUser admin,
                                        @PathVariable Long userId,
                                        HttpServletRequest request) {
        adminUserService.banUser(admin, userId, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    /** 解封 */
    @PostMapping("/{userId}/unban")
    public ResponseEntity<Void> unbanUser(@CurrentUser SysUser admin,
                                          @PathVariable Long userId,
                                          HttpServletRequest request) {
        adminUserService.unbanUser(admin, userId, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

}
