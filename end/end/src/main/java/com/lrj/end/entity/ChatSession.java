package com.lrj.end.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天会话（对应 chat_session 表）。
 * 多用户改造后：
 *  - userId 做数据隔离，任何查询都必须带用户条件；
 *  - deleted/deletedAt 实现逻辑删除（用户删除后数据保留，管理员可物理删除）；
 *  - lastActiveAt 记录最近发消息时间，供 90 天定时清理任务判定过期。
 */
@Data
@NoArgsConstructor
public class ChatSession {
    private Long id;
    /** 归属用户 id */
    private Long userId;
    private String title;
    /** 逻辑删除标记：0-正常，1-已删除（列表查询过滤） */
    private Boolean deleted;
    /** 逻辑删除时间 */
    private LocalDateTime deletedAt;
    /** 最近活动时间（发消息/改名都会刷新），90 天无活动会被物理清理 */
    private LocalDateTime lastActiveAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
