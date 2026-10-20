package com.lrj.end.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 关键操作日志（对应 operation_log 表）。
 * 防篡改设计：Mapper 只提供 insert，全系统没有任何 update/delete 该表的接口；
 * 查询接口仅管理员可用。即使业务代码有漏洞也无法改动历史日志。
 */
@Data
@NoArgsConstructor
public class OperationLog {
    private Long id;
    /** 操作人 ID；系统任务（如定时清理）为 NULL */
    private Long userId;
    /** 操作人用户名快照：用户被删除后日志仍可读 */
    private String username;
    /** 操作类型：LOGIN / REGISTER / BAN_USER / UNBAN_USER / RESET_PASSWORD /
     *  DELETE_SESSION / ADMIN_DELETE_SESSION / PURGE_SESSION 等 */
    private String action;
    /** 被操作对象类型，如 user / session，可空 */
    private String targetType;
    /** 被操作对象 ID，可空 */
    private String targetId;
    /** 补充说明，如重置密码、清理了多少条会话 */
    private String detail;
    private String ip;
    private LocalDateTime createdAt;
}
