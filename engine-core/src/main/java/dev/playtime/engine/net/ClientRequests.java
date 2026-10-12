package dev.playtime.engine.net;

import dev.playtime.engine.hand.LauncherSide;
import dev.playtime.engine.util.Id;

import java.util.UUID;

/**
 * Requests a client may send. They carry intent only (which launcher, which option),
 * never positions, targets or results; the server derives everything else itself.
 */
public final class ClientRequests {
    private ClientRequests() {}

    public record FireHand(LauncherSide side) {}

    public record PullHand(LauncherSide side) {}

    public record ReleaseHand(LauncherSide side) {}

    public record SelectHand(LauncherSide side, Id hand) {}

    public record MenuContinue(int slot) {}

    public record MenuNewGame(int slot) {}

    public record MenuConfirmNewGame(UUID token) {}

    public record MenuClosed() {}

    public record DialogueChoice(int option) {}

    /** Operator-only editor save: a content kind folder name and the JSON text. */
    public record SaveDefinition(String kind, String json) {}
}
