package com.lrj.end.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端会话视图：chat_session 联表 sys_user 带出用户名，含逻辑删除标记。
 */
@Data
public class AdminSessionView {
    private Long id;
    private Long userId;
    private String username;
    private String title;
    private Boolean deleted;
    private LocalDateTime deletedAt;
    private LocalDateTime lastActiveAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
