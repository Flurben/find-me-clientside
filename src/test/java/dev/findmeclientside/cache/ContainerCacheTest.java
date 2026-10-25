package dev.findmeclientside.cache;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class ContainerCacheTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final ItemSnapshot DIAMOND = new ItemSnapshot("minecraft:diamond", "", 1);

    private RememberedContainer chest(int x, String dimension, List<ItemSnapshot> items, long time) {
        return new RememberedContainer(dimension, "Chest", "block",
                List.of(new BlockAnchor(x, 64, 0, "minecraft:chest")), items, time);
    }

    @Test void searchesOnlyCurrentDimensionAndRadius() {
        ContainerCache cache = new ContainerCache();
        cache.remember(chest(12, OVERWORLD, List.of(DIAMOND), 10));
        cache.remember(chest(2, OVERWORLD, List.of(DIAMOND), 10));
        cache.remember(chest(3, "minecraft:the_nether", List.of(DIAMOND), 10));
        cache.remember(chest(100, OVERWORLD, List.of(DIAMOND), 10));
        var result = cache.search(OVERWORLD, 0.5, 64.5, 0.5, 16, List.of(DIAMOND), false);
        assertEquals(Set.of(2, 12), result.stream().map(c -> c.anchors().get(0).x()).collect(Collectors.toSet()));
    }

    @Test void radiusBoundaryUsesThreeDimensionalDistance() {
        ContainerCache cache = new ContainerCache();
        cache.remember(chest(5, OVERWORLD, List.of(DIAMOND), 10));
        assertEquals(1, cache.search(OVERWORLD, 0.5, 64.5, 0.5, 5, List.of(DIAMOND), false).size());
        assertTrue(cache.search(OVERWORLD, 0.5, 65.5, 0.5, 5, List.of(DIAMOND), false).isEmpty());
    }

    @Test void nbtCanBeExactOrIgnored() {
        var enchanted = new ItemSnapshot("minecraft:diamond_sword", "{Damage:4,custom:1}", 1);
        var query = new ItemSnapshot("minecraft:diamond_sword", "", 1);
        var container = chest(0, OVERWORLD, List.of(enchanted), 1);
        assertEquals(1, container.matchingCount(List.of(query), false));
        assertEquals(0, container.matchingCount(List.of(query), true));
        assertEquals(1, container.matchingCount(List.of(enchanted), true));
    }

    @Test void fluidsAndItemsWithTheSameIdNeverMatchEachOther() {
        var water = new ItemSnapshot("minecraft:water", "", 16_000, true);
        var container = chest(0, OVERWORLD, List.of(water, new ItemSnapshot("minecraft:water_bucket", "", 2)), 1);
        assertEquals(0, container.matchingCount(List.of(new ItemSnapshot("minecraft:water", "", 1)), false));
        assertEquals(16_000, container.matchingCount(new QuerySet(List.of(water)), false, true));
        assertEquals(0, container.matchingCount(new QuerySet(List.of(water)), false, false));
    }

    @Test void alternativeIngredientsCountEachStackOnce() {
        var container = chest(0, OVERWORLD, List.of(new ItemSnapshot("minecraft:diamond", "", 64),
                new ItemSnapshot("minecraft:emerald", "", 7)), 1);
        assertEquals(71, container.matchingCount(List.of(DIAMOND, DIAMOND,
                new ItemSnapshot("minecraft:emerald", "", 1)), false));
    }

    @Test void openingAnEmptyContainerRemovesOldMatches() {
        ContainerCache cache = new ContainerCache();
        cache.remember(chest(0, OVERWORLD, List.of(DIAMOND), 1));
        cache.remember(chest(0, OVERWORLD, List.of(), 2));
        assertEquals(1, cache.size());
        assertTrue(cache.search(OVERWORLD, 0, 64, 0, 64, List.of(DIAMOND), false).isEmpty());
    }

    @Test void eitherHalfOfDoubleChestReplacesTheSameRecord() {
        ContainerCache cache = new ContainerCache();
        var left = new BlockAnchor(0, 64, 0, "minecraft:chest");
        var right = new BlockAnchor(1, 64, 0, "minecraft:chest");
        cache.remember(new RememberedContainer(OVERWORLD, "Large Chest", "block",
                List.of(left, right), List.of(DIAMOND), 1));
        cache.remember(new RememberedContainer(OVERWORLD, "Large Chest", "block",
                List.of(right, left), List.of(new ItemSnapshot("minecraft:diamond", "", 32)), 2));
        assertEquals(1, cache.size());
        assertEquals(32, cache.entries().get(0).matchingCount(List.of(DIAMOND), false));
        // Breaking the double chest into singles must not retain the combined inventory.
        cache.remember(chest(1, OVERWORLD, List.of(), 3));
        assertEquals(1, cache.size());
        assertEquals(1, cache.entries().get(0).anchors().size());
    }

    @Test void enderChestUpdatesEveryKnownAccessPointAcrossDimensions() {
        ContainerCache cache = new ContainerCache();
        var overworld = new RememberedContainer(OVERWORLD, "Ender Chest", "ender_chest",
                List.of(new BlockAnchor(0, 64, 0, "minecraft:ender_chest")), List.of(DIAMOND), 1);
        var nether = new RememberedContainer("minecraft:the_nether", "Ender Chest", "ender_chest",
                List.of(new BlockAnchor(5, 64, 0, "minecraft:ender_chest")), List.of(), 2);
        cache.remember(overworld);
        cache.remember(nether);
        assertEquals(2, cache.size());
        assertTrue(cache.entries().stream().allMatch(c -> c.items().isEmpty()));
        assertTrue(cache.entries().stream().allMatch(c -> c.observedAt() == 2));
    }

    @Test void removesExpiredEntriesAndEvictsOldestWhenFull() {
        ContainerCache cache = new ContainerCache();
        for (int i = 0; i < 5; i++) cache.remember(chest(i, OVERWORLD, List.of(DIAMOND), i));
        cache.prune(1, 2);
        assertEquals(List.of(4L, 3L), cache.entries().stream().map(RememberedContainer::observedAt).toList());
        cache.prune(5, 2);
        assertEquals(0, cache.size());
    }

    @Test void removedAndEvictedContainersReleaseTheirPositions() {
        ContainerCache cache = new ContainerCache();
        var left = new BlockAnchor(0, 64, 0, "minecraft:chest");
        var right = new BlockAnchor(1, 64, 0, "minecraft:chest");
        cache.remember(new RememberedContainer(OVERWORLD, "Large Chest", "block", List.of(left, right), List.of(DIAMOND), 1));
        cache.removeIf(c -> true);
        // A stale index entry for the right half would make this second chest evict the first.
        cache.remember(chest(0, OVERWORLD, List.of(DIAMOND), 2));
        cache.remember(chest(1, OVERWORLD, List.of(DIAMOND), 3));
        assertEquals(2, cache.size());
        cache.remember(chest(5, OVERWORLD, List.of(DIAMOND), 4));
        cache.prune(0, 2);
        cache.remember(chest(1, OVERWORLD, List.of(), 5));
        assertEquals(Set.of(1, 5), cache.entries().stream().map(c -> c.anchors().get(0).x()).collect(Collectors.toSet()));
    }

    @Test void loadingKeepsTheNewestSnapshotPerPositionAndSharesTheLatestEnderContents() {
        ContainerCache cache = new ContainerCache();
        var emerald = new ItemSnapshot("minecraft:emerald", "", 3);
        var ender = new BlockAnchor(9, 64, 0, "minecraft:ender_chest");
        var otherEnder = new BlockAnchor(9, 64, 0, "minecraft:ender_chest");
        cache.load(List.of(
                chest(0, OVERWORLD, List.of(emerald), 7),
                chest(0, OVERWORLD, List.of(DIAMOND), 3),
                new RememberedContainer(OVERWORLD, "Ender Chest", "ender_chest", List.of(ender), List.of(DIAMOND), 2),
                new RememberedContainer("minecraft:the_nether", "Ender Chest", "ender_chest", List.of(otherEnder), List.of(emerald), 6)));
        assertFalse(cache.isDirty());
        assertEquals(3, cache.size());
        assertEquals(List.of(emerald), cache.search(OVERWORLD, 0, 64, 0, 4, List.of(emerald), false).get(0).items());
        assertTrue(cache.entries().stream().filter(c -> c.kind().equals("ender_chest"))
                .allMatch(c -> c.items().equals(List.of(emerald)) && c.observedAt() == 6));
    }

    @Test void invalidatingOneDimensionPreservesTheOther() {
        ContainerCache cache = new ContainerCache();
        cache.remember(chest(0, OVERWORLD, List.of(DIAMOND), 1));
        cache.remember(chest(0, "minecraft:the_nether", List.of(DIAMOND), 1));
        cache.markSaved();
        cache.removeIf(c -> c.dimension().equals(OVERWORLD));
        assertTrue(cache.isDirty());
        assertEquals("minecraft:the_nether", cache.entries().get(0).dimension());
    }
}
