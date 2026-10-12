package dev.playtime.engine.config;

import java.util.UUID;

public class MultiplayerConfig {
    public ProgressionMode progressionMode = ProgressionMode.INDIVIDUAL;
    /** Owner of the save in {@link ProgressionMode#HOST_OWNED}; the first joining player when null. */
    public UUID hostPlayer;
    public TargetPolicy targetPolicy = TargetPolicy.NEAREST;
    public ResetAuthority resetAuthority = ResetAuthority.HOST_AND_OPERATORS;
    /** A chase fails for everyone when any one participant is caught (otherwise only that player). */
    public boolean chaseFailsForAll = false;
    /** Players further than this from a chase trigger are not pulled into the chase. */
    public double chaseParticipationRadius = 48.0;
}
