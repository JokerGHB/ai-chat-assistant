package com.lrj.end.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 平台用户（对应 sys_user 表）。
 * 密码只存 BCrypt 哈希，任何接口都不返回 passwordHash 字段（见 dto.UserProfileResponse）。
 */
@Data
@NoArgsConstructor
public class SysUser {
    private Long id;
    private String username;
    private String passwordHash;
    /** 角色：user-普通用户，admin-管理员 */
    private String role;
    /** 状态：active-正常，banned-已封禁（无法登录，已有令牌也会失效） */
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
