package dev.findmeclientside.cache;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record RememberedContainer(String dimension, String title, String kind,
                                  List<BlockAnchor> anchors, List<ItemSnapshot> items, long observedAt) {
    public RememberedContainer {
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(title);
        Objects.requireNonNull(kind);
        anchors = anchors.stream().sorted(Comparator.comparingInt(BlockAnchor::x)
                .thenComparingInt(BlockAnchor::y).thenComparingInt(BlockAnchor::z)).toList();
        items = List.copyOf(items);
        if (anchors.isEmpty() || anchors.size() > 2) throw new IllegalArgumentException("Invalid anchors");
    }

    public boolean withinRadius(double x, double y, double z, double radius) {
        return anchors.stream().anyMatch(a -> a.distanceSquared(x, y, z) <= radius * radius);
    }

    public long matchingCount(List<ItemSnapshot> queries, boolean matchNbt) {
        return items.stream().filter(item -> queries.stream().anyMatch(q -> item.matches(q, matchNbt)))
                .mapToLong(ItemSnapshot::count).sum();
    }

    public boolean overlaps(RememberedContainer other) {
        return dimension.equals(other.dimension)
                && anchors.stream().anyMatch(a -> other.anchors.stream().anyMatch(a::samePosition));
    }
}
