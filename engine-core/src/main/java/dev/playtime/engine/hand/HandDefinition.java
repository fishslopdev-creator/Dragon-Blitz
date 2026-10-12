package dev.playtime.engine.hand;

import dev.playtime.engine.grabpack.CableSettings;
import dev.playtime.engine.grabpack.TargetKind;
import dev.playtime.engine.util.Id;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data-driven description of a GrabPack hand. New hands are added by dropping a JSON
 * file into {@code playtime/content/hands/}; no Java changes are needed unless the hand
 * introduces a brand-new ability handler.
 */
public class HandDefinition {
    public Id id;
    public String displayName;
    public String description = "";
    public Id icon;
    public Id model;
    public Id texture;
    /** Animation keys such as {@code fire}, {@code retract}, {@code equip}, {@code use}. */
    public Map<String, Id> animations = new LinkedHashMap<>();
    /** Sound keys such as {@code fire}, {@code attach}, {@code retract}, {@code use}, {@code pickup}. */
    public Map<String, Id> sounds = new LinkedHashMap<>();
    /** Particle/effect keys such as {@code pulse}, {@code flare_trail}. */
    public Map<String, Id> effects = new LinkedHashMap<>();
    /** ARGB colour for the wheel and cable tint. */
    public int color = 0xFF3A6FE0;
    public LauncherSide launcher = LauncherSide.RIGHT;
    /** GrabPack versions that can mount this hand; empty means all. */
    public Set<Id> grabPacks = new LinkedHashSet<>();
    public Set<Id> abilities = new LinkedHashSet<>();
    public Set<TargetKind> targets = EnumSet.noneOf(TargetKind.class);
    /** Interaction tags matched against scanners, doors and puzzle receivers (for example {@code playtime:green}). */
    public Set<Id> interactions = new LinkedHashSet<>();
    public double range = 24.0;
    public int cooldownTicks = 10;
    /** Uses before the hand must recharge (0 = unlimited). */
    public int charges;
    public int rechargeTicks = 100;
    /** Battery/energy cost per use (0 = free). */
    public int energyCost;
    public CableSettings cable = new CableSettings();
    public UnlockCondition unlock = new UnlockCondition();
    public List<HandUpgrade> upgrades = new ArrayList<>();
    /** When set, unlocking this hand replaces the given hand in the wheel (Omni-Hand replaces Red Hand). */
    public Id replaces;
    /** Tutorial shown the first time this hand is unlocked. */
    public Id tutorial;
    /** Ability-specific numbers, e.g. {@code pulse_radius}, {@code flare_light_ticks}, {@code charge_ticks}. */
    public Map<String, Double> params = new LinkedHashMap<>();
    /** Wheel ordering. */
    public int sortOrder;

    public HandDefinition normalize() {
        if (displayName == null) displayName = id == null ? "Hand" : id.path();
        if (description == null) description = "";
        if (animations == null) animations = new LinkedHashMap<>();
        if (sounds == null) sounds = new LinkedHashMap<>();
        if (effects == null) effects = new LinkedHashMap<>();
        if (launcher == null) launcher = LauncherSide.RIGHT;
        if (grabPacks == null) grabPacks = new LinkedHashSet<>();
        if (abilities == null) abilities = new LinkedHashSet<>();
        if (targets == null) targets = EnumSet.noneOf(TargetKind.class);
        if (interactions == null) interactions = new LinkedHashSet<>();
        if (cable == null) cable = new CableSettings();
        if (unlock == null) unlock = new UnlockCondition();
        if (upgrades == null) upgrades = new ArrayList<>();
        if (params == null) params = new LinkedHashMap<>();
        range = Math.max(1.0, Math.min(128.0, range));
        cooldownTicks = Math.max(0, cooldownTicks);
        cable.maxLength = Math.min(cable.maxLength, range);
        return this;
    }

    public double param(String key, double fallback) {
        Double v = params == null ? null : params.get(key);
        return v == null ? fallback : v;
    }

    public boolean fitsGrabPack(Id grabPack) {
        return grabPacks.isEmpty() || grabPack != null && grabPacks.contains(grabPack);
    }
}
