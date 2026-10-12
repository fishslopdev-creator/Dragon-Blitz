package dev.playtime.engine.content;

import dev.playtime.engine.access.CredentialDefinition;
import dev.playtime.engine.access.DoorDefinition;
import dev.playtime.engine.chase.ChaseDefinition;
import dev.playtime.engine.grabpack.GrabPackDefinition;
import dev.playtime.engine.hand.HandDefinition;
import dev.playtime.engine.hand.HandPickupConfig;
import dev.playtime.engine.monster.MonsterDefinition;
import dev.playtime.engine.monster.RouteDefinition;
import dev.playtime.engine.profile.PlayerProfile;
import dev.playtime.engine.profile.ProfileAssignment;
import dev.playtime.engine.puzzle.PuzzleDefinition;
import dev.playtime.engine.story.ChapterDefinition;
import dev.playtime.engine.story.CheckpointDefinition;
import dev.playtime.engine.story.CollectibleDefinition;
import dev.playtime.engine.story.DialogueTree;
import dev.playtime.engine.story.ObjectiveDefinition;
import dev.playtime.engine.story.StoryEvent;

import java.util.Locale;

/**
 * Each kind of map content and the folder it lives in under
 * {@code <world>/playtime/content/}. One JSON file per definition.
 */
public enum ContentKind {
    HANDS("hands", HandDefinition.class),
    GRABPACKS("grabpacks", GrabPackDefinition.class),
    CREDENTIALS("credentials", CredentialDefinition.class),
    DOORS("doors", DoorDefinition.class),
    PUZZLES("puzzles", PuzzleDefinition.class),
    MONSTERS("monsters", MonsterDefinition.class),
    ROUTES("routes", RouteDefinition.class),
    CHASES("chases", ChaseDefinition.class),
    EVENTS("events", StoryEvent.class),
    DIALOGUES("dialogues", DialogueTree.class),
    COLLECTIBLES("collectibles", CollectibleDefinition.class),
    OBJECTIVES("objectives", ObjectiveDefinition.class),
    CHAPTERS("chapters", ChapterDefinition.class),
    CHECKPOINTS("checkpoints", CheckpointDefinition.class),
    PROFILES("profiles", PlayerProfile.class),
    PROFILE_ASSIGNMENTS("profile_assignments", ProfileAssignment.class),
    PICKUPS("pickups", HandPickupConfig.class);

    public final String folder;
    public final Class<?> type;

    ContentKind(String folder, Class<?> type) {
        this.folder = folder;
        this.type = type;
    }

    public static ContentKind byFolder(String folder) {
        for (ContentKind k : values()) if (k.folder.equals(folder)) return k;
        return null;
    }

    public static ContentKind parse(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (ContentKind k : values()) if (k.folder.equals(n) || k.name().equalsIgnoreCase(n)) return k;
        return null;
    }
}
