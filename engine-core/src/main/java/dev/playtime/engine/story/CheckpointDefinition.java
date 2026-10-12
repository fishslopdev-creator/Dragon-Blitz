package dev.playtime.engine.story;

import dev.playtime.engine.util.BlockRef;
import dev.playtime.engine.util.Id;

public class CheckpointDefinition {
    public Id id;
    public String name;
    public BlockRef pos;
    public float yaw;
    public Id chapter;
    /** Checkpoint blocks save automatically when a player walks over them. */
    public boolean autoSave = true;
}
