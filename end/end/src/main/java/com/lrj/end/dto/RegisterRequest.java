package com.lrj.end.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。密码规则（需求 2.1）：6-12 位，支持大小写字母/数字/符号，
 * 且必须同时包含字母和数字（字符组合约束），防止纯数字弱密码。
 */
public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 2, max = 50, message = "用户名长度需为 2-50 位")
        String username,

        @NotBlank(message = "密码不能为空")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d!@#$%^&*()_+\\-=\\[\\]{};:'\",.<>/?\\\\|`~]{6,12}$",
                message = "密码需为 6-12 位，支持大小写字母、数字、符号，且须同时包含字母和数字"
        )
        String password
) {
}
