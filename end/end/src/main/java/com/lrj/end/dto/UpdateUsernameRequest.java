package com.lrj.end.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 修改用户名请求：规则与注册一致（2-50 位非空） */
public record UpdateUsernameRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 2, max = 50, message = "用户名长度需为 2-50 位")
        String username
) {
}
