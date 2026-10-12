package dev.playtime.engine.hand;

import dev.playtime.engine.grabpack.TargetKind;
import dev.playtime.engine.util.Id;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A configurable improvement to a hand, active once the save has {@link #requiresFlag}.
 * Lets one hand definition cover several in-game versions instead of duplicating it.
 */
public class HandUpgrade {
    public Id id;
    public String displayName;
    public Id requiresFlag;
    public Double range;
    public Integer cooldownTicks;
    public Integer charges;
    public Set<Id> addAbilities = new LinkedHashSet<>();
    public Set<TargetKind> addTargets = new LinkedHashSet<>();
    public Set<Id> addInteractions = new LinkedHashSet<>();
}
