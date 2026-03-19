package com.bidops.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * SecurityContext에서 현재 사용자 ID를 추출하는 유틸.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static String currentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) return "anonymous";
            String name = auth.getName();
            return "anonymousUser".equals(name) ? "anonymous" : name;
        } catch (Exception e) {
            return "anonymous";
        }
    }
}
