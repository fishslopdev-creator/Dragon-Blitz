package dev.playtime.engine;

import dev.playtime.engine.access.DoorDefinition;
import dev.playtime.engine.engine.GameBridge;
import dev.playtime.engine.engine.MenuPayload;
import dev.playtime.engine.engine.PlayerPosition;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** In-memory stand-in for Minecraft that records everything the engine asks for. */
public final class FakeBridge implements GameBridge {
    public static final class FakePlayer {
        public final UUID id;
        public String name;
        public boolean op;
        public String team;
        public PlayerPosition pos = new PlayerPosition(BlockRef.OVERWORLD, 0, 64, 0);
        public final Set<Id> credentials = new HashSet<>();
        public final Map<Id, Integer> items = new LinkedHashMap<>();
        public final List<String> statuses = new ArrayList<>();
        public final List<String> titles = new ArrayList<>();
        public final List<String> objectives = new ArrayList<>();
        public final List<String> unlocks = new ArrayList<>();
        public final List<BlockRef> teleports = new ArrayList<>();
        public MenuPayload lastMenu;
        public int menusOpened;
        public DialogueSession.View dialogue;
        public StartingKit kit;
        public float damage;
        public boolean killed;

        FakePlayer(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public final Map<UUID, FakePlayer> players = new LinkedHashMap<>();
    public final Map<UUID, MonsterDefinition> monsters = new HashMap<>();
    public final Map<Id, Boolean> doors = new HashMap<>();
    public final List<String> sounds = new ArrayList<>();
    public final List<String> lights = new ArrayList<>();

    public FakePlayer add(String name) {
        UUID id = UUID.nameUUIDFromBytes(name.getBytes());
        FakePlayer p = new FakePlayer(id, name);
        players.put(id, p);
        return p;
    }

    public FakePlayer p(UUID id) {
        return players.get(id);
    }

    @Override public Collection<UUID> onlinePlayers() { return List.copyOf(players.keySet()); }
    @Override public boolean isOperator(UUID player) { return players.containsKey(player) && players.get(player).op; }
    @Override public String playerName(UUID player) { return players.containsKey(player) ? players.get(player).name : "?"; }
    @Override public PlayerPosition position(UUID player) { return players.containsKey(player) ? players.get(player).pos : null; }
    @Override public String team(UUID player) { return players.containsKey(player) ? players.get(player).team : null; }

    @Override public void showTitle(UUID player, String title, String subtitle, int ticks) { p(player).titles.add(title); }
    @Override public void showStatus(UUID player, String message) { if (player != null && p(player) != null) p(player).statuses.add(message); }
    @Override public void showObjective(UUID player, String text, int ticksLeft) { if (p(player) != null) p(player).objectives.add(text); }
    @Override public void showUnlock(UUID player, String title, String description, Id icon) { p(player).unlocks.add(title); }
    @Override public void openDialogue(UUID player, DialogueSession.View view) { p(player).dialogue = view; }
    @Override public void closeDialogue(UUID player) { p(player).dialogue = null; }
    @Override public void openMainMenu(UUID player, MenuPayload payload) { p(player).lastMenu = payload; p(player).menusOpened++; }
    @Override public void playRecording(UUID player, CollectibleDefinition collectible) { sounds.add("recording:" + collectible.id); }
    @Override public void showCredits(UUID player, Id credits) { p(player).titles.add("credits"); }
    @Override public void playSound(UUID player, Id sound, BlockRef pos, float volume) { sounds.add(String.valueOf(sound)); }
    @Override public void stopSound(UUID player, Id sound) { sounds.add("stop:" + sound); }
    @Override public void particles(Id particle, BlockRef pos, int count) { }
    @Override public void cameraShake(UUID player, int ticks, float strength) { }
    @Override public void screenFade(UUID player, int ticks) { }
    @Override public void setLights(Box area, boolean on) { lights.add(on ? "on" : "off"); }
    @Override public void deviceChanged(Device device, DeviceState state) { }
    @Override public void doorChanged(DoorDefinition door, boolean open) { doors.put(door.id, open); }

    @Override
    public void giveItem(UUID player, ItemSpec item) {
        p(player).items.merge(item.item, item.count, Integer::sum);
    }

    @Override public void applyStartingKit(UUID player, StartingKit kit) { p(player).kit = kit; }

    @Override
    public void consumeHeldItem(UUID player, Id item) {
        p(player).items.computeIfPresent(item, (k, v) -> v > 1 ? v - 1 : null);
    }

    @Override public Set<Id> heldCredentials(UUID player) { return p(player) == null ? Set.of() : p(player).credentials; }

    @Override public boolean removeCredentialItem(UUID player, Id credential) { return p(player).credentials.remove(credential); }

    @Override
    public boolean hasItems(UUID player, Set<Id> items) {
        return p(player).items.keySet().containsAll(items);
    }

    @Override
    public void teleport(UUID player, BlockRef pos, float yaw) {
        FakePlayer fp = p(player);
        fp.teleports.add(pos);
        fp.pos = new PlayerPosition(pos.dimension(), pos.x() + 0.5, pos.y(), pos.z() + 0.5);
    }

    @Override
    public UUID spawnMonster(MonsterDefinition def, BlockRef pos) {
        UUID id = UUID.randomUUID();
        monsters.put(id, def);
        return id;
    }

    @Override public void despawnMonster(UUID entity) { monsters.remove(entity); }
    @Override public boolean isMonsterAlive(UUID entity) { return monsters.containsKey(entity); }

    @Override
    public void damagePlayer(UUID player, UUID monster, float amount, boolean kill) {
        p(player).damage += amount;
        p(player).killed |= kill;
    }

    @Override public void syncPlayer(UUID player) { }

    public void moveTo(UUID player, BlockRef pos) {
        p(player).pos = new PlayerPosition(pos.dimension(), pos.x() + 0.5, pos.y(), pos.z() + 0.5);
    }
}
