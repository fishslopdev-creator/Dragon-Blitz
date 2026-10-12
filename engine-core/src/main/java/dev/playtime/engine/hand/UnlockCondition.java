package dev.playtime.engine.hand;

import dev.playtime.engine.util.Id;

import java.util.LinkedHashSet;
import java.util.Set;

/** Requirements before a hand pickup can be collected. Empty means "always". */
public class UnlockCondition {
    public Set<Id> chapters = new LinkedHashSet<>();
    public Set<Id> flags = new LinkedHashSet<>();
    public Set<Id> hands = new LinkedHashSet<>();
    public Set<Id> puzzles = new LinkedHashSet<>();
    public String lockedMessage;
}
