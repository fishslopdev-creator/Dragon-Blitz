package dev.playtime.engine.hand;

import dev.playtime.engine.util.Id;

/** Settings stored on a placed hand pickup. */
public class HandPickupConfig {
    /** Unique pickup id so "already collected" is tracked per pickup, not per hand. */
    public Id id;
    public Id hand;
    public PickupMode mode = PickupMode.PERMANENT;
    public Id chapter;
    /** Objective to set active after collecting. */
    public Id objective;
    /** Story event fired after collecting. */
    public Id event;
    /** Equip the new hand immediately. */
    public boolean autoEquip = true;
}
