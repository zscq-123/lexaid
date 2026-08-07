package com.lexaid.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户工具类
 */
public final class UserContext {

    private UserContext() {
    }

    public static LoginUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser user) {
            return user;
        }
        return null;
    }

    public static Long userId() {
        LoginUser user = current();
        return user == null ? null : user.getId();
    }

    public static String username() {
        LoginUser user = current();
        return user == null ? null : user.getUsername();
    }
}
