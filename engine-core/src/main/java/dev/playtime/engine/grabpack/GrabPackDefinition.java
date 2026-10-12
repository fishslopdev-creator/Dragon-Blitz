package dev.playtime.engine.grabpack;

import dev.playtime.engine.util.Id;

/** A GrabPack model/version (for example the original pack, GrabPack 2.0, or a map's own design). */
public class GrabPackDefinition {
    public Id id;
    public String displayName = "GrabPack";
    public Id model;
    public Id texture;
    /** Cable length cap for every hand used on this pack. */
    public double cableLength = 24.0;
    /** Whether hands can be swapped in the field with the hand wheel. */
    public boolean handWheel = true;
    /** Number of hand slots on the right launcher selectable via the wheel. */
    public int rightLauncherSlots = 4;
    public boolean hasLeftLauncher = true;
}
