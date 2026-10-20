package com.lrj.end.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 聊天消息（对应 chat_message 表）。
 *  - userId 冗余归属用户，便于按人统计每日对话次数（不必 join 会话表）；
 *  - cost 仅 assistant 消息有值，写入时按配置的 token 单价计算，供统计看板汇总；
 *  - status=failed 的 assistant 消息即"未解决对话"，AI 调用失败时写入。
 */
@Data
@NoArgsConstructor
public class ChatMessage {
    private Long id;
    private Long sessionId;
    /** 归属用户 id（冗余列，按人统计用） */
    private Long userId;
    private String role;
    private String content;
    /** complete-正常，failed-AI 调用失败（未解决对话） */
    private String status;
    private String errorMessage;
    private Integer promptTokens;
    private Integer completionTokens;
    /** 本次 assistant 回复的大模型调用成本（元） */
    private BigDecimal cost;
    private LocalDateTime createdAt;
}
