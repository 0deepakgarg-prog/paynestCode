package com.paynest.config.security;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class JWTUtils {

    public static String getCurrentAccountId() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }

    public static String getCurrentAccountType() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        Claims claims = (Claims) authentication.getDetails();
        return claims.get("scope").toString();
    }

    public static String getCurrentTenant() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        Claims claims = (Claims) authentication.getDetails();
        return claims.get("tenant").toString();
    }

}

