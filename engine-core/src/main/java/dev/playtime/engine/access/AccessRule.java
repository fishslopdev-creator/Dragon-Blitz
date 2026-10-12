package dev.playtime.engine.access;

import dev.playtime.engine.logic.Condition;
import dev.playtime.engine.util.Id;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Who may open a door or use a scanner/terminal. All configured parts must pass;
 * an empty rule always grants access.
 */
public class AccessRule {
    /** Any one of these credentials opens it. */
    public Set<Id> credentials = new LinkedHashSet<>();
    /** Any credential with clearance at or above this opens it (0 disables). */
    public int minClearance;
    public boolean consumeCredential;
    /** Hands that must all be unlocked. */
    public Set<Id> requiredHands = new LinkedHashSet<>();
    /** At least one of these hands must be unlocked. */
    public Set<Id> anyHands = new LinkedHashSet<>();
    /** For scanners: the presented hand must be one of these... */
    public Set<Id> scanHands = new LinkedHashSet<>();
    /** ...or carry one of these interaction tags (e.g. {@code playtime:red_scanner}). */
    public Set<Id> scanTags = new LinkedHashSet<>();
    /** The device must currently receive power. */
    public boolean requiresPower;
    public List<Condition> conditions = new ArrayList<>();
    public String deniedMessage;

    public boolean needsCredential() {
        return credentials != null && !credentials.isEmpty() || minClearance > 0;
    }

    public boolean needsScan() {
        return scanHands != null && !scanHands.isEmpty() || scanTags != null && !scanTags.isEmpty();
    }
}
