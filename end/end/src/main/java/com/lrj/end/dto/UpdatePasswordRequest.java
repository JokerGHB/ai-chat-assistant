package com.lrj.end.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 修改密码请求：需验证旧密码，新密码规则与注册一致。 */
public record UpdatePasswordRequest(
        @NotBlank(message = "旧密码不能为空")
        String oldPassword,

        @NotBlank(message = "新密码不能为空")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d!@#$%^&*()_+\\-=\\[\\]{};:'\",.<>/?\\\\|`~]{6,12}$",
                message = "新密码需为 6-12 位，且须同时包含字母和数字"
        )
        String newPassword
) {
}
