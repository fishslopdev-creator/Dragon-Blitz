package dev.playtime.engine.access;

import dev.playtime.engine.util.Id;

import java.util.List;

public record AccessResult(boolean granted, Id consumedCredential, List<String> reasons) {
    public String message() {
        return reasons.isEmpty() ? "" : String.join(" ", reasons);
    }
}
