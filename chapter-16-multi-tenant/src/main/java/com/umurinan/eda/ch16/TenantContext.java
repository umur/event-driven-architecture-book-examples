package com.umurinan.eda.ch16;

public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>(); // (1)

    private TenantContext() {}

    public static void set(String tenantId) {
        CURRENT.set(tenantId); // (2)
    }

    public static String get() {
        return CURRENT.get(); // (3)
    }

    public static void clear() {
        CURRENT.remove(); // (4)
    }
}
