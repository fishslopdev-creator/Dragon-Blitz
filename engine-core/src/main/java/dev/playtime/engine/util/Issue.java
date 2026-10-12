package dev.playtime.engine.util;

/** One problem found while loading or validating map content. */
public record Issue(Severity severity, String source, String message) {
    public enum Severity { INFO, WARNING, ERROR }

    public static Issue error(String source, String message) { return new Issue(Severity.ERROR, source, message); }

    public static Issue warning(String source, String message) { return new Issue(Severity.WARNING, source, message); }

    public static Issue info(String source, String message) { return new Issue(Severity.INFO, source, message); }

    @Override
    public String toString() {
        return "[" + severity + "] " + source + ": " + message;
    }
}
