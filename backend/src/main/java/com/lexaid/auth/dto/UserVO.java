package com.lexaid.auth.dto;

/**
 * 用户信息视图
 */
public record UserVO(Long id, String username, String nickname, String role) {

    public static UserVO from(com.lexaid.auth.User user) {
        return new UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getRole());
    }
}
