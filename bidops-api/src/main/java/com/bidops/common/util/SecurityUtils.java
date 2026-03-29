package com.bidops.common.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    @SuppressWarnings("unchecked")
    public static String getCurrentOrganizationId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getDetails() == null) return null;
        if (auth.getDetails() instanceof Map) {
            String orgId = ((Map<String, String>) auth.getDetails()).get("organizationId");
            return (orgId != null && !orgId.isEmpty()) ? orgId : null;
        }
        return null;
    }
}
