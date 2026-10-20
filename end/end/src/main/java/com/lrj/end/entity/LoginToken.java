package com.lrj.end.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 登录令牌（对应 login_token 表）。
 * 采用服务端令牌而非 JWT：封禁用户、强制下线时可以立即删除记录使令牌失效。
 * lastActiveAt 每次请求刷新，超过配置的闲置分钟数未活动则视为过期（滑动过期）。
 */
@Data
@NoArgsConstructor
public class LoginToken {
    private Long id;
    /** 64 位十六进制随机串，前端放在 Authorization: Bearer 头中携带 */
    private String token;
    private Long userId;
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;
}
