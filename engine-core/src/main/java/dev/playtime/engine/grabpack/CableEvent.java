package dev.playtime.engine.grabpack;

/** Something the cable did this tick that gameplay or presentation should react to. */
public record CableEvent(Type type, TargetInfo target, String detail) {
    public enum Type {
        FIRED,
        ATTACHED,
        MISSED,
        PULL_STARTED,
        RELEASED,
        SNAPPED,
        RETRACTED,
        REJECTED
    }
}
