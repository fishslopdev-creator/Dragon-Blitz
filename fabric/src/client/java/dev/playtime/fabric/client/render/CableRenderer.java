package dev.playtime.fabric.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.playtime.fabric.client.ClientState;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Draws GrabPack cables from each player's launcher to the hand, plus a small hand marker. */
public final class CableRenderer {
    private CableRenderer() {}

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(CableRenderer::render);
    }

    private static void render(WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ClientState.CABLES.isEmpty()) return;
        Vec3 cam = ctx.worldState().cameraRenderState.pos;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        PoseStack matrices = ctx.matrices();
        VertexConsumer lines = ctx.consumers().getBuffer(RenderTypes.lines());
        PoseStack.Pose pose = matrices.last();
        for (Map.Entry<UUID, List<ClientState.Cable>> e : ClientState.CABLES.entrySet()) {
            Player player = mc.level.getPlayerByUUID(e.getKey());
            if (player == null) continue;
            Vec3 pos = player.getPosition(partial);
            double yaw = Math.toRadians(player.getViewYRot(partial));
            double eye = player.getEyeHeight() - 0.35;
            for (ClientState.Cable c : e.getValue()) {
                double s = "left".equals(c.side()) ? -0.35 : 0.35;
                Vec3 from = new Vec3(pos.x - Math.cos(yaw) * s, pos.y + eye, pos.z - Math.sin(yaw) * s);
                Vec3 to = new Vec3(c.x(), c.y(), c.z());
                int color = ClientState.handColor(c.hand());
                line(pose, lines, from.subtract(cam), to.subtract(cam), 0xFF2A2A2E, 2.5F);
                double r = 0.18;
                Vec3 hand = to.subtract(cam);
                line(pose, lines, hand.add(-r, 0, 0), hand.add(r, 0, 0), color, 6.0F);
                line(pose, lines, hand.add(0, -r, 0), hand.add(0, r, 0), color, 6.0F);
                line(pose, lines, hand.add(0, 0, -r), hand.add(0, 0, r), color, 6.0F);
            }
        }
    }

    private static void line(PoseStack.Pose pose, VertexConsumer lines, Vec3 a, Vec3 b, int color, float width) {
        Vector3f n = new Vector3f((float) (b.x - a.x), (float) (b.y - a.y), (float) (b.z - a.z));
        if (n.lengthSquared() < 1.0e-6F) return;
        n.normalize();
        lines.addVertex(pose, (float) a.x, (float) a.y, (float) a.z).setColor(color).setNormal(pose, n).setLineWidth(width);
        lines.addVertex(pose, (float) b.x, (float) b.y, (float) b.z).setColor(color).setNormal(pose, n).setLineWidth(width);
    }
}
