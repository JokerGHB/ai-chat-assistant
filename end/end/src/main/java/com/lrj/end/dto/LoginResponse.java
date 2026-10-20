package com.lrj.end.dto;

/**
 * 登录成功响应：token 由前端保存（localStorage），后续请求放在 Authorization: Bearer 头中。
 */
public record LoginResponse(String token, UserProfileResponse user) {
}
