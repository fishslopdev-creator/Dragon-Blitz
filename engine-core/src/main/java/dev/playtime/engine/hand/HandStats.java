package dev.playtime.engine.hand;

import dev.playtime.engine.grabpack.TargetKind;
import dev.playtime.engine.util.Id;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** A hand's effective numbers after applying every upgrade the save has earned. */
public record HandStats(HandDefinition definition, double range, int cooldownTicks, int charges,
                        Set<Id> abilities, Set<TargetKind> targets, Set<Id> interactions) {

    public static HandStats of(HandDefinition def, Set<Id> saveFlags) {
        double range = def.range;
        int cooldown = def.cooldownTicks;
        int charges = def.charges;
        Set<Id> abilities = new LinkedHashSet<>(def.abilities);
        Set<TargetKind> targets = def.targets.isEmpty() ? EnumSet.noneOf(TargetKind.class) : EnumSet.copyOf(def.targets);
        Set<Id> interactions = new LinkedHashSet<>(def.interactions);
        for (HandUpgrade up : def.upgrades) {
            if (up == null || up.requiresFlag != null && (saveFlags == null || !saveFlags.contains(up.requiresFlag))) continue;
            if (up.range != null) range = up.range;
            if (up.cooldownTicks != null) cooldown = up.cooldownTicks;
            if (up.charges != null) charges = up.charges;
            if (up.addAbilities != null) abilities.addAll(up.addAbilities);
            if (up.addTargets != null) targets.addAll(up.addTargets);
            if (up.addInteractions != null) interactions.addAll(up.addInteractions);
        }
        return new HandStats(def, range, cooldown, charges, abilities, targets, interactions);
    }

    public boolean has(Id ability) {
        return abilities.contains(ability);
    }

    public boolean canTarget(TargetKind kind) {
        return targets.contains(kind);
    }
}
