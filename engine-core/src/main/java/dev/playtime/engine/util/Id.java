package dev.playtime.engine.util;

import java.util.Objects;

/**
 * A namespaced identifier such as {@code playtime:blue_hand}. Mirrors Minecraft's
 * identifier rules so values can be passed straight to the game layer.
 */
public record Id(String namespace, String path) implements Comparable<Id> {
    public static final String DEFAULT_NAMESPACE = "playtime";

    public Id {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (!isValidNamespace(namespace)) {
            throw new IllegalArgumentException("Invalid identifier namespace '" + namespace + "'");
        }
        if (!isValidPath(path)) {
            throw new IllegalArgumentException("Invalid identifier path '" + path + "'");
        }
    }

    /** Parses {@code ns:path}; a bare {@code path} uses the {@code playtime} namespace. */
    public static Id parse(String text) {
        Objects.requireNonNull(text, "identifier");
        String trimmed = text.trim();
        int colon = trimmed.indexOf(':');
        if (colon < 0) {
            return new Id(DEFAULT_NAMESPACE, trimmed);
        }
        return new Id(trimmed.substring(0, colon), trimmed.substring(colon + 1));
    }

    /** Like {@link #parse} but returns {@code null} instead of throwing. */
    public static Id tryParse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return parse(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Id of(String path) {
        return new Id(DEFAULT_NAMESPACE, path);
    }

    public static boolean isValidNamespace(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-' || c == '.')) {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidPath(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-' || c == '.' || c == '/')) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int compareTo(Id o) {
        int c = namespace.compareTo(o.namespace);
        return c != 0 ? c : path.compareTo(o.path);
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
