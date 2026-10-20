package com.lrj.end.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 未解决对话明细项：AI 调用失败的 assistant 消息，联表带出用户名。
 */
@Data
public class UnresolvedMessageView {
    private Long id;
    private Long sessionId;
    private Long userId;
    private String username;
    private String content;
    private String errorMessage;
    private LocalDateTime createdAt;
}
