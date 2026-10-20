package com.lrj.end.dto;

/**
 * 管理员重置密码结果：把生成的新密码明文返回给管理员（仅此一次，不落明文）。
 */
public record ResetPasswordResponse(String newPassword) {
}
