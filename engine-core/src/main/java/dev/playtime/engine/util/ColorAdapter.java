package dev.playtime.engine.util;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Locale;

/**
 * ARGB colours in JSON. Reads {@code "#RRGGBB"}, {@code "#AARRGGBB"} or a number (0 to 0xFFFFFFFF);
 * writes {@code "#AARRGGBB"}. Six-digit colours are made fully opaque.
 */
public final class ColorAdapter extends TypeAdapter<Integer> {
    @Override
    public void write(JsonWriter out, Integer value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(String.format(Locale.ROOT, "#%08X", value));
        }
    }

    @Override
    public Integer read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        if (in.peek() == JsonToken.NUMBER) {
            long v = in.nextLong();
            if (v < Integer.MIN_VALUE || v > 0xFFFFFFFFL) throw new JsonParseException("colour out of range at " + in.getPath());
            return (int) v;
        }
        String s = in.nextString().trim();
        if (s.startsWith("#")) s = s.substring(1);
        else if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
        try {
            if (s.length() == 6) return (int) (0xFF000000L | Long.parseLong(s, 16));
            if (s.length() == 8) return (int) Long.parseLong(s, 16);
        } catch (NumberFormatException ignored) {
            // fall through
        }
        throw new JsonParseException("expected a colour like \"#RRGGBB\" or \"#AARRGGBB\" at " + in.getPath());
    }
}
