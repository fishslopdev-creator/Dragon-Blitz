package dev.playtime.engine.access;

import com.google.gson.annotations.JsonAdapter;
import dev.playtime.engine.util.ColorAdapter;
import dev.playtime.engine.util.Id;

/** A key, keycard or access token. Map creators define as many as they like. */
public class CredentialDefinition {
    public Id id;
    public String displayName;
    public CredentialKind kind = CredentialKind.KEYCARD;
    /** ARGB colour used for the item tint and reader lights. */
    @JsonAdapter(ColorAdapter.class)
    public int color = 0xFF2F6BDB;
    /** Security clearance; doors with {@code minClearance} accept any card at or above it. */
    public int clearance;
    public Id model;
    public String description = "";
    /** Whether the item is a "key item" kept in progress (true) or a physical inventory item only. */
    public boolean trackInProgress = true;
}
