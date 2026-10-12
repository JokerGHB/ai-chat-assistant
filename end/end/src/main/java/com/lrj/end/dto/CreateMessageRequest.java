package com.lrj.end.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMessageRequest(
        @NotBlank(message = "消息内容不能为空")
        @Size(max = 50000, message = "消息内容不能超过 50000 个字符")
        String content
) {
}
