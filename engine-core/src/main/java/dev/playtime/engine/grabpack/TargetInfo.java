package dev.playtime.engine.grabpack;

import dev.playtime.engine.util.Id;
import dev.playtime.engine.util.Vec3;

/**
 * A resolved target. {@code device} is the engine device id for blocks registered in
 * the connection graph, or {@code null} for plain blocks/entities.
 */
public record TargetInfo(TargetKind kind, Vec3 position, Id device, String entityRef) {
    public static TargetInfo device(TargetKind kind, Vec3 pos, Id device) {
        return new TargetInfo(kind, pos, device, null);
    }

    public static TargetInfo entity(TargetKind kind, Vec3 pos, String entityRef) {
        return new TargetInfo(kind, pos, null, entityRef);
    }

    public static TargetInfo surface(Vec3 pos) {
        return new TargetInfo(TargetKind.SURFACE, pos, null, null);
    }
}
