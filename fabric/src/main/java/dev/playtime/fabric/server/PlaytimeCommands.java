package dev.playtime.fabric.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.playtime.engine.admin.AdminResult;
import dev.playtime.engine.admin.AdminService;
import dev.playtime.engine.content.ContentKind;
import dev.playtime.engine.engine.PlaytimeEngine;
import dev.playtime.engine.util.Id;
import dev.playtime.fabric.Ids;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * {@code /playtime ...}. Privileged branches require game-master permission here, and
 * {@link AdminService} checks the permission again on the engine side.
 */
public final class PlaytimeCommands {
    private PlaytimeCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> build(dispatcher));
    }

    private static UUID actor(CommandSourceStack source) {
        ServerPlayer p = source.getPlayer();
        return p == null ? null : p.getUUID();
    }

    private static int run(CommandContext<CommandSourceStack> ctx, Function<AdminService, AdminResult> action) {
        PlaytimeEngine engine = EngineHolder.engine();
        if (engine == null) {
            ctx.getSource().sendFailure(Component.literal("Playtime Engine is not running in this world."));
            return 0;
        }
        AdminResult r = action.apply(new AdminService(engine));
        for (String line : r.lines()) {
            if (r.ok()) ctx.getSource().sendSuccess(() -> Component.literal(line), false);
            else ctx.getSource().sendFailure(Component.literal(line));
        }
        return r.ok() ? 1 : 0;
    }

    private static Id id(CommandContext<CommandSourceStack> ctx, String name) {
        return Ids.engine(IdentifierArgument.getId(ctx, name));
    }

    private static UUID player(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        return EntityArgument.getPlayer(ctx, name).getUUID();
    }

    private static SuggestionProvider<CommandSourceStack> suggest(ContentKind kind) {
        return (ctx, builder) -> {
            PlaytimeEngine engine = EngineHolder.engine();
            List<String> ids = new ArrayList<>();
            if (engine != null) {
                for (Object o : engine.content().all(kind)) {
                    Id i = dev.playtime.engine.content.ContentLoader.idOf(o);
                    if (i != null) ids.add(i.toString());
                }
            }
            return SharedSuggestionProvider.suggest(ids, builder);
        };
    }

    private static SuggestionProvider<CommandSourceStack> suggestDevices() {
        return (ctx, builder) -> {
            PlaytimeEngine engine = EngineHolder.engine();
            List<String> ids = new ArrayList<>();
            if (engine != null) engine.graph().devices().forEach(d -> ids.add(d.id.toString()));
            return SharedSuggestionProvider.suggest(ids, builder);
        };
    }

    private static void build(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = literal("playtime");

        // Anyone: reopen your own menu (if the world allows it).
        root.then(literal("menu")
                .executes(ctx -> run(ctx, a -> a.openMenu(actor(ctx.getSource()), actor(ctx.getSource()))))
                .then(argument("player", EntityArgument.player()).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> run(ctx, a -> a.openMenu(actor(ctx.getSource()), uuid(ctx, "player")))))
                .then(literal("bypass").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(argument("on", BoolArgumentType.bool())
                                .executes(ctx -> run(ctx, a -> a.setMenuBypass(actor(ctx.getSource()), actor(ctx.getSource()),
                                        BoolArgumentType.getBool(ctx, "on"))))
                                .then(argument("player", EntityArgument.player())
                                        .executes(ctx -> run(ctx, a -> a.setMenuBypass(actor(ctx.getSource()), uuid(ctx, "player"),
                                                BoolArgumentType.getBool(ctx, "on"))))))));

        root.then(literal("inspect").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(argument("player", EntityArgument.player())
                        .executes(ctx -> run(ctx, a -> a.inspect(actor(ctx.getSource()), uuid(ctx, "player"))))));

        root.then(literal("hand").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(literal("give").then(argument("player", EntityArgument.player())
                        .then(argument("hand", IdentifierArgument.id()).suggests(suggest(ContentKind.HANDS))
                                .executes(ctx -> run(ctx, a -> a.giveHand(actor(ctx.getSource()), uuid(ctx, "player"), id(ctx, "hand")))))))
                .then(literal("revoke").then(argument("player", EntityArgument.player())
                        .then(argument("hand", IdentifierArgument.id()).suggests(suggest(ContentKind.HANDS))
                                .executes(ctx -> run(ctx, a -> a.revokeHand(actor(ctx.getSource()), uuid(ctx, "player"), id(ctx, "hand"))))))));

        root.then(literal("checkpoint").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(argument("player", EntityArgument.player())
                        .then(argument("checkpoint", IdentifierArgument.id())
                                .executes(ctx -> run(ctx, a -> a.setCheckpoint(actor(ctx.getSource()), uuid(ctx, "player"), id(ctx, "checkpoint"), false)))
                                .then(literal("tp").executes(ctx -> run(ctx, a -> a.setCheckpoint(actor(ctx.getSource()),
                                        uuid(ctx, "player"), id(ctx, "checkpoint"), true)))))));

        root.then(literal("save").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(literal("reset").then(argument("player", EntityArgument.player())
                        .executes(ctx -> run(ctx, a -> a.resetSave(actor(ctx.getSource()), uuid(ctx, "player"), false)))
                        .then(literal("world").executes(ctx -> run(ctx, a -> a.resetSave(actor(ctx.getSource()), uuid(ctx, "player"), true)))))));

        root.then(literal("puzzle").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(literal("reset")
                        .executes(ctx -> run(ctx, a -> a.resetPuzzle(actor(ctx.getSource()), null)))
                        .then(argument("puzzle", IdentifierArgument.id()).suggests(suggest(ContentKind.PUZZLES))
                                .executes(ctx -> run(ctx, a -> a.resetPuzzle(actor(ctx.getSource()), id(ctx, "puzzle"))))))
                .then(literal("status")
                        .executes(ctx -> run(ctx, a -> a.puzzleStatus(actor(ctx.getSource()), null)))
                        .then(argument("puzzle", IdentifierArgument.id()).suggests(suggest(ContentKind.PUZZLES))
                                .executes(ctx -> run(ctx, a -> a.puzzleStatus(actor(ctx.getSource()), id(ctx, "puzzle")))))));

        root.then(literal("chase").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(literal("start").then(argument("chase", IdentifierArgument.id()).suggests(suggest(ContentKind.CHASES))
                        .executes(ctx -> run(ctx, a -> a.startChase(actor(ctx.getSource()), id(ctx, "chase"), actor(ctx.getSource()))))))
                .then(literal("stop").then(argument("chase", IdentifierArgument.id()).suggests(suggest(ContentKind.CHASES))
                        .executes(ctx -> run(ctx, a -> a.stopChase(actor(ctx.getSource()), id(ctx, "chase")))))));

        root.then(literal("monster").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(literal("list").executes(ctx -> run(ctx, a -> a.listMonsters(actor(ctx.getSource())))))
                .then(literal("despawn").executes(ctx -> run(ctx, a -> a.despawnAll(actor(ctx.getSource())))))
                .then(literal("spawn").then(argument("monster", IdentifierArgument.id()).suggests(suggest(ContentKind.MONSTERS))
                        .executes(ctx -> run(ctx, a -> a.spawnMonster(actor(ctx.getSource()), id(ctx, "monster"),
                                here(ctx.getSource(), BlockPos.containing(ctx.getSource().getPosition())))))
                        .then(argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> run(ctx, a -> a.spawnMonster(actor(ctx.getSource()), id(ctx, "monster"),
                                        here(ctx.getSource(), pos(ctx)))))))));

        root.then(literal("event").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(argument("event", IdentifierArgument.id()).suggests(suggest(ContentKind.EVENTS))
                        .executes(ctx -> run(ctx, a -> a.triggerEvent(actor(ctx.getSource()), id(ctx, "event"), actor(ctx.getSource()))))
                        .then(argument("player", EntityArgument.player())
                                .executes(ctx -> run(ctx, a -> a.triggerEvent(actor(ctx.getSource()), id(ctx, "event"), uuid(ctx, "player")))))));

        root.then(literal("connections").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> run(ctx, a -> a.connections(actor(ctx.getSource()), null)))
                .then(argument("device", IdentifierArgument.id()).suggests(suggestDevices())
                        .executes(ctx -> run(ctx, a -> a.connections(actor(ctx.getSource()), id(ctx, "device"))))));

        root.then(literal("link").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(literal("remove").then(argument("link", IdentifierArgument.id())
                        .executes(ctx -> run(ctx, a -> a.removeConnection(actor(ctx.getSource()), id(ctx, "link"))))))
                .then(literal("rename").then(argument("link", IdentifierArgument.id())
                        .then(argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> run(ctx, a -> a.renameConnection(actor(ctx.getSource()), id(ctx, "link"),
                                        StringArgumentType.getString(ctx, "name"))))))));

        root.then(literal("test").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(argument("device", IdentifierArgument.id()).suggests(suggestDevices())
                        .executes(ctx -> run(ctx, a -> a.testDevice(actor(ctx.getSource()), id(ctx, "device"))))));

        root.then(literal("validate").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> run(ctx, a -> a.validate(actor(ctx.getSource())))));
        root.then(literal("reload").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> run(ctx, a -> a.reload(actor(ctx.getSource())))));
        root.then(literal("backup").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> run(ctx, a -> a.backup(actor(ctx.getSource())))));

        root.then(literal("profile").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(literal("list").executes(ctx -> run(ctx, a -> a.listProfiles(actor(ctx.getSource())))))
                .then(literal("assign").then(argument("player", EntityArgument.player())
                        .then(argument("profile", IdentifierArgument.id()).suggests(suggest(ContentKind.PROFILES))
                                .executes(ctx -> run(ctx, a -> a.assignProfile(actor(ctx.getSource()), uuid(ctx, "player"), id(ctx, "profile")))))))
                .then(literal("apply").then(argument("player", EntityArgument.player())
                        .then(argument("profile", IdentifierArgument.id()).suggests(suggest(ContentKind.PROFILES))
                                .executes(ctx -> run(ctx, a -> a.applyProfile(actor(ctx.getSource()), uuid(ctx, "player"), id(ctx, "profile"))))))));

        // Editors (operators, in-game JSON editing).
        root.then(literal("settings").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> {
                    Editors.openWorldSettings(ctx.getSource().getPlayerOrException());
                    return 1;
                }));
        LiteralArgumentBuilder<CommandSourceStack> edit = literal("edit");
        edit.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        for (ContentKind kind : ContentKind.values()) {
            edit.then(literal(kind.folder).then(argument("id", IdentifierArgument.id()).suggests(suggest(kind))
                    .executes(ctx -> {
                        Editors.openDefinitionEditor(ctx.getSource().getPlayerOrException(), kind, id(ctx, "id"));
                        return 1;
                    })));
        }
        root.then(edit);
        root.then(literal("device").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(argument("device", IdentifierArgument.id()).suggests(suggestDevices())
                        .executes(ctx -> {
                            Editors.openDeviceEditor(ctx.getSource().getPlayerOrException(), id(ctx, "device"));
                            return 1;
                        })));

        root.then(literal("sample").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(literal("install").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    return SampleBuilder.install(ctx.getSource(), p) ? 1 : 0;
                })));

        dispatcher.register(root);
    }

    private static UUID uuid(CommandContext<CommandSourceStack> ctx, String name) {
        try {
            return player(ctx, name);
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static BlockPos pos(CommandContext<CommandSourceStack> ctx) {
        try {
            return BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static dev.playtime.engine.util.BlockRef here(CommandSourceStack source, BlockPos pos) {
        return Ids.ref(source.getLevel(), pos);
    }
}
