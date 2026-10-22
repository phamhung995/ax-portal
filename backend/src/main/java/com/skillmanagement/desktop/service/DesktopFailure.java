package com.skillmanagement.desktop.service;

public final class DesktopFailure extends RuntimeException {
    private final int status;
    private final String code;
    public DesktopFailure(int status,String code,String message) { super(message); this.status=status; this.code=code; }
    public int status() { return status; }
    public String code() { return code; }
    public static DesktopFailure query() { return new DesktopFailure(400,"INVALID_QUERY","The desktop catalog query is invalid."); }
}
