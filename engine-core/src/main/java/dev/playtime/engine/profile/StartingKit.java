package dev.playtime.engine.profile;

import dev.playtime.engine.util.Id;

import java.util.List;

/** Items the game layer must hand out after a profile has been applied to a new save. */
public record StartingKit(Id grabPack, List<ItemSpec> items, List<Id> keyItems, int batteries,
                          boolean canSprint, boolean canCrouch) {}
