package dev.playtime.engine.profile;

import dev.playtime.engine.util.Id;

/**
 * An item and quantity for a starting inventory. {@code components} is an optional
 * item-components string in vanilla command syntax (for example {@code [custom_name='"Fuse"']}).
 */
public class ItemSpec {
    public Id item;
    public int count = 1;
    /** Inventory slot 0-35, or -1 for "first free slot". */
    public int slot = -1;
    public String components;

    public ItemSpec() {}

    public ItemSpec(Id item, int count) {
        this.item = item;
        this.count = count;
    }
}
