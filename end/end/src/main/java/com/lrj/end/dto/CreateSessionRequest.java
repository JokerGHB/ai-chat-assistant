package com.lrj.end.dto;

import jakarta.validation.constraints.Size;

/**
 * 创建会话请求。模型已固定为 deepseek-flash，不再支持选择模型配置。
 */
public record CreateSessionRequest(
        @Size(max = 200, message = "会话标题不能超过 200 个字符") String title
) {
}
