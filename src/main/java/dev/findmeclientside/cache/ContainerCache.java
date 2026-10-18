package dev.findmeclientside.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public final class ContainerCache {
    private final List<RememberedContainer> containers = new ArrayList<>();
    private boolean dirty;

    public void remember(RememberedContainer snapshot) {
        // Every ender chest belongs to this session's player, even across dimensions.
        if (snapshot.kind().equals("ender_chest")) {
            containers.replaceAll(old -> old.kind().equals("ender_chest")
                    ? new RememberedContainer(old.dimension(), old.title(), old.kind(), old.anchors(),
                    snapshot.items(), snapshot.observedAt()) : old);
        }
        containers.removeIf(old -> old.overlaps(snapshot));
        containers.add(snapshot);
        dirty = true;
    }

    public void load(Collection<RememberedContainer> loaded) {
        containers.clear();
        loaded.stream().sorted(Comparator.comparingLong(RememberedContainer::observedAt)).forEach(this::remember);
        dirty = false;
    }

    public List<RememberedContainer> search(String dimension, double x, double y, double z,
                                           double radius, List<ItemSnapshot> queries, boolean matchNbt) {
        return containers.stream().filter(c -> c.dimension().equals(dimension))
                .filter(c -> c.withinRadius(x, y, z, radius))
                .filter(c -> c.matchingCount(queries, matchNbt) > 0)
                .toList();
    }

    public void prune(long oldestAllowed, int maximum) {
        removeIf(c -> c.observedAt() < oldestAllowed);
        containers.sort(Comparator.comparingLong(RememberedContainer::observedAt).reversed());
        if (containers.size() > maximum) {
            containers.subList(maximum, containers.size()).clear();
            dirty = true;
        }
    }

    public void removeIf(Predicate<RememberedContainer> predicate) {
        if (containers.removeIf(predicate)) dirty = true;
    }

    public void clear() {
        containers.clear();
        dirty = true;
    }

    public List<RememberedContainer> entries() { return List.copyOf(containers); }
    public int size() { return containers.size(); }
    public boolean isDirty() { return dirty; }
    public void markSaved() { dirty = false; }
}
