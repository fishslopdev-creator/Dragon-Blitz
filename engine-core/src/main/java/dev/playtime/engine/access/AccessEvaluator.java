package dev.playtime.engine.access;

import dev.playtime.engine.logic.Condition;
import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.IdSets;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Checks an {@link AccessRule} and explains every failed requirement to the player. */
public final class AccessEvaluator {
    private final Map<Id, CredentialDefinition> credentials;

    public AccessEvaluator(Map<Id, CredentialDefinition> credentials) {
        this.credentials = credentials;
    }

    public AccessResult evaluate(AccessRule rule, AccessRequest req) {
        List<String> reasons = new ArrayList<>();
        if (rule == null) return new AccessResult(true, null, reasons);

        Id usedCredential = null;
        if (rule.needsCredential()) {
            usedCredential = findCredential(rule, req.heldCredentials());
            if (usedCredential == null) reasons.add(describeMissingCredential(rule));
        }
        if (!IdSets.containsAll(req.unlockedHands(), rule.requiredHands)) {
            reasons.add("Requires " + names(rule.requiredHands) + ".");
        }
        if (!IdSets.containsAny(req.unlockedHands(), rule.anyHands)) {
            reasons.add("Requires one of: " + names(rule.anyHands) + ".");
        }
        if (rule.needsScan()) {
            var hand = req.presentedHand();
            boolean scanOk = hand != null && (rule.scanHands.contains(hand.definition().id)
                    || IdSets.containsAny(hand.interactions(), rule.scanTags) && !rule.scanTags.isEmpty());
            if (!scanOk) {
                reasons.add(hand == null ? "Place a hand on the scanner." : "The scanner rejects the " + hand.definition().displayName + ".");
            }
        }
        if (rule.requiresPower && !req.devicePowered()) reasons.add("There's no power.");
        if (rule.conditions != null && !Condition.allMatch(rule.conditions, req.facts())) {
            reasons.add("It won't budge.");
        }
        if (!reasons.isEmpty() && rule.deniedMessage != null) {
            reasons.clear();
            reasons.add(rule.deniedMessage);
        }
        boolean granted = reasons.isEmpty();
        return new AccessResult(granted, granted && rule.consumeCredential ? usedCredential : null, reasons);
    }

    private Id findCredential(AccessRule rule, Set<Id> held) {
        if (held == null) return null;
        for (Id id : rule.credentials) if (held.contains(id)) return id;
        if (rule.minClearance > 0) {
            for (Id id : held) {
                CredentialDefinition def = credentials.get(id);
                if (def != null && def.clearance >= rule.minClearance) return id;
            }
        }
        return null;
    }

    private String describeMissingCredential(AccessRule rule) {
        if (!rule.credentials.isEmpty()) return "Requires " + names(rule.credentials) + ".";
        return "Requires security clearance " + rule.minClearance + ".";
    }

    private String names(Set<Id> ids) {
        List<String> out = new ArrayList<>();
        for (Id id : ids) {
            CredentialDefinition def = credentials.get(id);
            out.add(def != null && def.displayName != null ? def.displayName : pretty(id));
        }
        return String.join(" or ", out);
    }

    static String pretty(Id id) {
        String[] parts = id.path().replace('/', ' ').split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
