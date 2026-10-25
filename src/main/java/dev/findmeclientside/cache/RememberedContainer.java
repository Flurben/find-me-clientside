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
        return matchingCount(new QuerySet(queries), matchNbt);
    }

    public long matchingCount(QuerySet queries, boolean matchNbt) {
        long total = 0;
        for (ItemSnapshot item : items) if (queries.matches(item, matchNbt)) total += item.count();
        return total;
    }

    /** Matching item count, or matching fluid amount in mB. */
    public long matchingCount(QuerySet queries, boolean matchNbt, boolean fluids) {
        long total = 0;
        for (ItemSnapshot item : items) if (item.fluid() == fluids && queries.matches(item, matchNbt)) total += item.count();
        return total;
    }

    public boolean overlaps(RememberedContainer other) {
        return dimension.equals(other.dimension)
                && anchors.stream().anyMatch(a -> other.anchors.stream().anyMatch(a::samePosition));
    }
}
