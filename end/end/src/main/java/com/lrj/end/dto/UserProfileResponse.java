package com.lrj.end.dto;

import com.lrj.end.entity.SysUser;

import java.time.LocalDateTime;

/**
 * 对外暴露的用户信息。注意：绝不包含 passwordHash。
 */
public record UserProfileResponse(
        Long id,
        String username,
        String role,
        String status,
        LocalDateTime createdAt,
        Integer dailyLimit,
        Integer usedToday,
        Integer remainingToday
) {
    /** 从实体转换，集中控制可暴露的字段（不含每日限额统计） */
    public static UserProfileResponse from(SysUser user) {
        return new UserProfileResponse(
                user.getId(), user.getUsername(), user.getRole(), user.getStatus(), user.getCreatedAt(),
                null, null, null);
    }

    /** 附带当日对话次数使用情况（每日限额口径：user 消息数） */
    public static UserProfileResponse from(SysUser user, int dailyLimit, int usedToday) {
        return new UserProfileResponse(
                user.getId(), user.getUsername(), user.getRole(), user.getStatus(), user.getCreatedAt(),
                dailyLimit, usedToday, Math.max(dailyLimit - usedToday, 0));
    }
}
