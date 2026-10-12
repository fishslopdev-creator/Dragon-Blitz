package dev.playtime.fabric.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.playtime.engine.util.Json;
import dev.playtime.fabric.PlaytimeMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Two channels carry every engine message: one server-to-client, one client-to-server.
 * Each message is a kind plus a JSON body, which keeps the protocol easy to extend.
 * Clients only ever send intent; the server validates everything it receives.
 */
public final class Payloads {
    public static final int MAX_JSON = 1 << 18;

    private Payloads() {}

    public record S2C(String kind, String json) implements CustomPacketPayload {
        public static final Type<S2C> TYPE = new Type<>(PlaytimeMod.id("s2c"));
        public static final StreamCodec<RegistryFriendlyByteBuf, S2C> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), S2C::kind, ByteBufCodecs.stringUtf8(MAX_JSON), S2C::json, S2C::new);

        public static S2C of(String kind, Object body) {
            return new S2C(kind, body instanceof JsonElement e ? e.toString() : Json.toJson(body));
        }

        public JsonObject body() {
            JsonElement e = JsonParser.parseString(json);
            return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2S(String kind, String json) implements CustomPacketPayload {
        public static final Type<C2S> TYPE = new Type<>(PlaytimeMod.id("c2s"));
        public static final StreamCodec<RegistryFriendlyByteBuf, C2S> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), C2S::kind, ByteBufCodecs.stringUtf8(MAX_JSON), C2S::json, C2S::new);

        public static C2S of(String kind, JsonObject body) {
            return new C2S(kind, body.toString());
        }

        public JsonObject body() {
            try {
                JsonElement e = JsonParser.parseString(json);
                return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
            } catch (RuntimeException ex) {
                return new JsonObject();
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(S2C.TYPE, S2C.CODEC);
        PayloadTypeRegistry.playC2S().register(C2S.TYPE, C2S.CODEC);
    }
}
