package dev.playtime.engine.profile;

import dev.playtime.engine.util.Box;
import dev.playtime.engine.util.Id;

/** Rule that selects a profile automatically. Higher priority wins; ties go to file order. */
public class ProfileAssignment {
    public Id id;
    public AssignmentType type = AssignmentType.PLAYER;
    /** Player UUID/name, team name or scenario flag id depending on {@link #type}. */
    public String match;
    public Box area;
    public Id profile;
    public int priority;
}
