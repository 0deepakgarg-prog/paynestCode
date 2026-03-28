package com.paynest.tenant;

public final class RequestLanguageContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private RequestLanguageContext() {
    }

    public static void setLanguage(String languageCode) {
        CURRENT.set(languageCode);
    }

    public static String getLanguage() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
