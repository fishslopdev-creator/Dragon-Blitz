package dev.playtime.engine.power;

import dev.playtime.engine.util.Id;

import java.util.Set;

/** Devices whose powered state changed in a recompute, plus generators that tripped from overload. */
public record PowerResult(Set<Id> nowPowered, Set<Id> nowUnpowered, Set<Id> tripped) {
    public boolean isEmpty() {
        return nowPowered.isEmpty() && nowUnpowered.isEmpty() && tripped.isEmpty();
    }
}
