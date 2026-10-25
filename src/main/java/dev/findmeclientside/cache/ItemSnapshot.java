package dev.findmeclientside.cache;

import java.util.Objects;

/**
 * Plain data so the cache can be tested without bootstrapping Minecraft. Despite the name it also holds
 * fluids: {@code itemId} is then the fluid's registry ID and {@code count} its amount in mB.
 * Cache files written before fluid support have no {@code fluid} field and load as items.
 */
public record ItemSnapshot(String itemId, String tag, long count, boolean fluid) {
    public ItemSnapshot {
        Objects.requireNonNull(itemId);
        tag = tag == null ? "" : tag;
        if (itemId.isBlank() || count <= 0) throw new IllegalArgumentException("Invalid cached item");
    }

    public ItemSnapshot(String itemId, String tag, long count) { this(itemId, tag, count, false); }

    public boolean matches(ItemSnapshot query, boolean matchNbt) {
        return fluid == query.fluid && itemId.equals(query.itemId) && (!matchNbt || tag.equals(query.tag));
    }
}
