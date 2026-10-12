package dev.playtime.engine.engine;

import dev.playtime.engine.access.DoorDefinition;
import dev.playtime.engine.graph.Device;
import dev.playtime.engine.graph.DeviceState;
import dev.playtime.engine.monster.MonsterDefinition;
import dev.playtime.engine.profile.ItemSpec;
import dev.playtime.engine.profile.StartingKit;
import dev.playtime.engine.story.CollectibleDefinition;
import dev.playtime.engine.story.DialogueSession;
import dev.playtime.engine.util.BlockRef;
import dev.playtime.engine.util.Box;
import dev.playtime.engine.util.Id;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Everything the engine asks of the game. The Fabric module implements this against
 * Minecraft; tests implement it with a recorder. Methods are called on the server thread.
 * Presentation methods only send packets to clients; they never decide gameplay.
 */
public interface GameBridge {
    // --------------------------------------------------------------- players
    Collection<UUID> onlinePlayers();

    boolean isOperator(UUID player);

    String playerName(UUID player);

    PlayerPosition position(UUID player);

    String team(UUID player);

    // --------------------------------------------------------------- presentation (client side)
    void showTitle(UUID player, String title, String subtitle, int ticks);

    /** Short line above the hotbar (puzzle progress, access denied reasons). */
    void showStatus(UUID player, String message);

    /** Objective tracker update; {@code ticksLeft > 0} shows a countdown. */
    void showObjective(UUID player, String text, int ticksLeft);

    /** Large toast for unlocks ("Green Hand acquired"). */
    void showUnlock(UUID player, String title, String description, Id icon);

    void openDialogue(UUID player, DialogueSession.View view);

    void closeDialogue(UUID player);

    void openMainMenu(UUID player, MenuPayload payload);

    void playRecording(UUID player, CollectibleDefinition collectible);

    void showCredits(UUID player, Id credits);

    /** Plays a sound at a position for everyone nearby, or only to {@code player} when non-null and pos is null. */
    void playSound(UUID player, Id sound, BlockRef pos, float volume);

    void stopSound(UUID player, Id sound);

    void particles(Id particle, BlockRef pos, int count);

    void cameraShake(UUID player, int ticks, float strength);

    void screenFade(UUID player, int ticks);

    // --------------------------------------------------------------- world
    void setLights(Box area, boolean on);

    void deviceChanged(Device device, DeviceState state);

    void doorChanged(DoorDefinition door, boolean open);

    void giveItem(UUID player, ItemSpec item);

    void applyStartingKit(UUID player, StartingKit kit);

    /** Removes one of {@code item} from the player's main hand after it was inserted into a socket. */
    void consumeHeldItem(UUID player, Id item);

    /** Credential ids held in the player's inventory (cards and keys are real items). */
    Set<Id> heldCredentials(UUID player);

    boolean removeCredentialItem(UUID player, Id credential);

    boolean hasItems(UUID player, Set<Id> items);

    void teleport(UUID player, BlockRef pos, float yaw);

    /** Spawns a monster entity for a definition and returns the entity UUID, or null. */
    UUID spawnMonster(MonsterDefinition def, BlockRef pos);

    void despawnMonster(UUID entity);

    boolean isMonsterAlive(UUID entity);

    /** Hurts or kills the player for a monster attack. */
    void damagePlayer(UUID player, UUID monster, float amount, boolean kill);

    /** Pushes updated HUD/hand data to the client after progress changes. */
    void syncPlayer(UUID player);
}
