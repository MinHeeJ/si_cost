package com.sicost.permission;

import java.util.function.Supplier;

/** Trusted calculation input only: never changes the principal or grants response access. */
public final class PermissionCalculationContext {
    private static final ThreadLocal<Boolean> SOURCE=new ThreadLocal<>();
    private PermissionCalculationContext() {}
    public static boolean sourceRead() {return Boolean.TRUE.equals(SOURCE.get());}
    public static <T> T source(Supplier<T> read) {
        Boolean previous=SOURCE.get();
        try {SOURCE.set(Boolean.TRUE);return read.get();}
        finally {if(previous==null)SOURCE.remove();else SOURCE.set(previous);}
    }
}
