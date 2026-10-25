package dev.findmeclientside.cache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Searched items and fluids grouped by registry ID. Matching a stored entry is one hash lookup, however many
 * alternatives a tag ingredient expands to, which keeps large AE2 networks fast to search.
 */
public final class QuerySet {
    private final Map<String, List<ItemSnapshot>> items = new HashMap<>();
    private final Map<String, List<ItemSnapshot>> fluids = new HashMap<>();

    public QuerySet(List<ItemSnapshot> queries) {
        for (ItemSnapshot query : queries) {
            (query.fluid() ? fluids : items).computeIfAbsent(query.itemId(), id -> new ArrayList<>()).add(query);
        }
    }

    public boolean matches(ItemSnapshot stored, boolean matchNbt) {
        List<ItemSnapshot> candidates = (stored.fluid() ? fluids : items).get(stored.itemId());
        if (candidates == null) return false;
        if (!matchNbt) return true;
        for (ItemSnapshot query : candidates) if (stored.matches(query, true)) return true;
        return false;
    }
}
