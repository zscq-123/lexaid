package com.lexaid.auth.dto;

/**
 * 登录响应
 */
public record LoginResponse(String token, UserVO user) {
}
