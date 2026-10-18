package dev.findmeclientside.cache;

import java.util.Objects;

/** Plain data so the cache can be tested without bootstrapping Minecraft. */
public record ItemSnapshot(String itemId, String tag, int count) {
    public ItemSnapshot {
        Objects.requireNonNull(itemId);
        tag = tag == null ? "" : tag;
        if (itemId.isBlank() || count <= 0) throw new IllegalArgumentException("Invalid cached item");
    }

    public boolean matches(ItemSnapshot query, boolean matchNbt) {
        return itemId.equals(query.itemId) && (!matchNbt || tag.equals(query.tag));
    }
}
