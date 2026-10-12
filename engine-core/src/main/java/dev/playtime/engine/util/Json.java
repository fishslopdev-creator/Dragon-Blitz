package dev.playtime.engine.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Shared Gson configuration. Identifiers are written as strings and enum constants
 * as lower-case names (for example {@code "every_login"}) so JSON stays readable
 * for map creators.
 */
public final class Json {
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Id.class, new IdAdapter().nullSafe())
            .registerTypeAdapterFactory(new LowercaseEnumFactory())
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private Json() {}

    public static <T> T fromJson(String json, Class<T> type) {
        return GSON.fromJson(json, type);
    }

    public static <T> T fromJson(JsonElement json, Class<T> type) {
        return GSON.fromJson(json, type);
    }

    public static String toJson(Object o) {
        return GSON.toJson(o);
    }

    public static JsonElement toTree(Object o) {
        return GSON.toJsonTree(o);
    }

    public static JsonElement parse(String text) {
        return JsonParser.parseString(text);
    }

    /** Deep copy through JSON. Used for snapshots of definitions and state. */
    public static <T> T copy(T value, Class<T> type) {
        return GSON.fromJson(GSON.toJsonTree(value), type);
    }

    private static final class IdAdapter extends TypeAdapter<Id> {
        @Override
        public void write(JsonWriter out, Id value) throws IOException {
            out.value(value.toString());
        }

        @Override
        public Id read(JsonReader in) throws IOException {
            String s = in.nextString();
            try {
                return Id.parse(s);
            } catch (IllegalArgumentException e) {
                throw new JsonParseException(e.getMessage() + " at " + in.getPath(), e);
            }
        }
    }

    private static final class LowercaseEnumFactory implements TypeAdapterFactory {
        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            Class<? super T> raw = type.getRawType();
            if (!Enum.class.isAssignableFrom(raw) || raw == Enum.class) {
                return null;
            }
            if (!raw.isEnum()) {
                raw = raw.getSuperclass();
            }
            return (TypeAdapter<T>) new EnumAdapter(raw);
        }
    }

    private static final class EnumAdapter<E extends Enum<E>> extends TypeAdapter<E> {
        private final Class<E> type;
        private final Map<String, E> byName = new HashMap<>();

        EnumAdapter(Class<E> type) {
            this.type = type;
            for (E constant : type.getEnumConstants()) {
                byName.put(constant.name().toLowerCase(Locale.ROOT), constant);
            }
        }

        @Override
        public void write(JsonWriter out, E value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.name().toLowerCase(Locale.ROOT));
            }
        }

        @Override
        public E read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String raw = in.nextString();
            E value = byName.get(raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_'));
            if (value == null) {
                throw new JsonParseException("Unknown " + type.getSimpleName() + " '" + raw + "' at " + in.getPath()
                        + "; expected one of " + byName.keySet());
            }
            return value;
        }
    }
}
