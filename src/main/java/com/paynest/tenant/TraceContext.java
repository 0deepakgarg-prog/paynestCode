package com.paynest.tenant;

public class TraceContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    public static void setTraceId(String traceId) {
        CURRENT.set(traceId);
    }

    public static String getTraceId() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
