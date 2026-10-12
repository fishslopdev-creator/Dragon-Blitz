package dev.playtime.engine.config;

import dev.playtime.engine.util.Id;

public class AtmosphereConfig {
    public Id ambientLoop = Id.of("ambient.factory");
    public float ambientVolume = 0.5f;
    public Id chaseMusic = Id.of("music.chase");
    /** Hard cap on engine-spawned particles per tick per player. */
    public int particleBudgetPerTick = 64;
    public boolean fogEnabled = false;
}
