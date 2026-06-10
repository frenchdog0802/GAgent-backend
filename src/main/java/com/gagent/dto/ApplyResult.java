package com.gagent.dto;

public record ApplyResult(boolean success, String message) {
    public static ApplyResult ok() {
        return new ApplyResult(true, "ok");
    }

    public static ApplyResult fail(String msg) {
        return new ApplyResult(false, msg);
    }
}
