package dev.playtime.engine.admin;

import java.util.List;

public record AdminResult(boolean ok, List<String> lines) {
    public static AdminResult ok(String... lines) {
        return new AdminResult(true, List.of(lines));
    }

    public static AdminResult ok(List<String> lines) {
        return new AdminResult(true, List.copyOf(lines));
    }

    public static AdminResult fail(String message) {
        return new AdminResult(false, List.of(message));
    }
}
