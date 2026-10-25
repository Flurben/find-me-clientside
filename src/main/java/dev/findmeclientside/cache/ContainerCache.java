package dev.findmeclientside.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class ContainerCache {
    private record Position(String dimension, int x, int y, int z) {
        static Position of(String dimension, BlockAnchor anchor) {
            return new Position(dimension, anchor.x(), anchor.y(), anchor.z());
        }
    }

    // Keyed by each container's first anchor. Every anchor maps to that key, so replacing a
    // container (or either half of a double chest) is a lookup instead of a scan of the cache.
    private final Map<Position, RememberedContainer> containers = new LinkedHashMap<>();
    private final Map<Position, Position> owners = new HashMap<>();
    private boolean dirty;

    public void remember(RememberedContainer snapshot) {
        // Every ender chest belongs to this session's player, even across dimensions.
        if (snapshot.kind().equals("ender_chest")) shareEnderContents(snapshot);
        put(snapshot);
        dirty = true;
    }

    public void load(Collection<RememberedContainer> loaded) {
        clear();
        RememberedContainer latestEnder = null;
        for (RememberedContainer snapshot : loaded.stream()
                .sorted(Comparator.comparingLong(RememberedContainer::observedAt)).toList()) {
            put(snapshot);
            if (snapshot.kind().equals("ender_chest")) latestEnder = snapshot;
        }
        // Sharing once with the newest ender chest matches sharing at every step, without rescanning per entry.
        if (latestEnder != null) shareEnderContents(latestEnder);
        dirty = false;
    }

    public List<RememberedContainer> search(String dimension, double x, double y, double z,
                                           double radius, List<ItemSnapshot> queries, boolean matchNbt) {
        QuerySet querySet = new QuerySet(queries);
        return containers.values().stream().filter(c -> c.dimension().equals(dimension))
                .filter(c -> c.withinRadius(x, y, z, radius))
                .filter(c -> c.matchingCount(querySet, matchNbt) > 0)
                .toList();
    }

    public void prune(long oldestAllowed, int maximum) {
        removeIf(c -> c.observedAt() < oldestAllowed);
        if (containers.size() <= maximum) return;
        List<RememberedContainer> newest = new ArrayList<>(containers.values());
        newest.sort(Comparator.comparingLong(RememberedContainer::observedAt).reversed());
        containers.clear();
        owners.clear();
        newest.subList(0, maximum).forEach(this::put);
        dirty = true;
    }

    public void removeIf(Predicate<RememberedContainer> predicate) {
        Iterator<RememberedContainer> iterator = containers.values().iterator();
        while (iterator.hasNext()) {
            RememberedContainer container = iterator.next();
            if (predicate.test(container)) {
                iterator.remove();
                container.anchors().forEach(a -> owners.remove(Position.of(container.dimension(), a)));
                dirty = true;
            }
        }
    }

    public void clear() {
        containers.clear();
        owners.clear();
        dirty = true;
    }

    public List<RememberedContainer> entries() { return List.copyOf(containers.values()); }
    public int size() { return containers.size(); }
    public boolean isDirty() { return dirty; }
    public void markSaved() { dirty = false; }

    private void put(RememberedContainer snapshot) {
        for (BlockAnchor anchor : snapshot.anchors()) {
            Position key = owners.get(Position.of(snapshot.dimension(), anchor));
            if (key == null) continue;
            RememberedContainer old = containers.remove(key);
            if (old != null) old.anchors().forEach(a -> owners.remove(Position.of(old.dimension(), a)));
        }
        Position key = Position.of(snapshot.dimension(), snapshot.anchors().get(0));
        containers.put(key, snapshot);
        snapshot.anchors().forEach(a -> owners.put(Position.of(snapshot.dimension(), a), key));
    }

    private void shareEnderContents(RememberedContainer snapshot) {
        containers.replaceAll((key, old) -> old.kind().equals("ender_chest")
                ? new RememberedContainer(old.dimension(), old.title(), old.kind(), old.anchors(),
                snapshot.items(), snapshot.observedAt()) : old);
    }
}
