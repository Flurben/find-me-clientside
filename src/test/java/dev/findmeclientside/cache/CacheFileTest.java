package dev.findmeclientside.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CacheFileTest {
    @TempDir Path directory;

    @Test void separatesServersWorldsAndPlayersWithoutPuttingAddressesInFilenames() {
        Path first = CacheFile.forSession(directory, "server:private.example|player:one");
        assertEquals(first, CacheFile.forSession(directory, "server:private.example|player:one"));
        assertNotEquals(first, CacheFile.forSession(directory, "server:other.example|player:one"));
        assertNotEquals(first, CacheFile.forSession(directory, "server:private.example|player:two"));
        assertNotEquals(first, CacheFile.forSession(directory, "local:private.example|player:one"));
        assertEquals(directory, first.getParent());
        assertTrue(first.getFileName().toString().matches("[a-f0-9]{64}\\.json"));
    }

    @Test void writesReadsAndAtomicallyReplacesSnapshotsIncludingNbtAndEmptyContainers() throws Exception {
        Path file = directory.resolve("cache.json");
        var container = new RememberedContainer("minecraft:overworld", "My \"valuables\"", "block",
                List.of(new BlockAnchor(-30, 64, 12, "minecraft:chest")),
                List.of(new ItemSnapshot("minecraft:enchanted_book", "{StoredEnchantments:[{lvl:3s}]}", 2)), 12345);
        CacheFile.write(file, List.of(container));
        assertEquals(List.of(container), CacheFile.read(file));
        CacheFile.write(file, List.of());
        assertTrue(CacheFile.read(file).isEmpty());
        assertFalse(Files.exists(directory.resolve("cache.json.tmp")));
    }

    @Test void roundTripsFluidsAndAmountsBeyondIntRange() throws Exception {
        Path file = directory.resolve("cache.json");
        var network = new RememberedContainer("minecraft:overworld", "ME Terminal", "me_network",
                List.of(new BlockAnchor(0, 64, 0, "ae2:cable_bus")),
                List.of(new ItemSnapshot("minecraft:cobblestone", "", 9_000_000_000L),
                        new ItemSnapshot("minecraft:water", "", 3_500_000_000L, true)), 1);
        CacheFile.write(file, List.of(network));
        assertEquals(List.of(network), CacheFile.read(file));
    }

    @Test void readsCachesWrittenBeforeFluidSupportAsItems() throws Exception {
        Path file = directory.resolve("cache.json");
        Files.writeString(file, """
                {"version":1,"containers":[{"dimension":"minecraft:overworld","title":"Chest","kind":"block",
                "anchors":[{"x":1,"y":64,"z":2,"blockId":"minecraft:chest"}],
                "items":[{"itemId":"minecraft:diamond","tag":"","count":5}],"observedAt":7}]}""");
        var item = CacheFile.read(file).get(0).items().get(0);
        assertEquals(new ItemSnapshot("minecraft:diamond", "", 5), item);
        assertFalse(item.fluid());
    }

    @Test void separateSuffixesKeepNetworksOutOfTheMainFile() {
        Path main = CacheFile.forSession(directory, "local:world|player:one");
        Path networks = CacheFile.forSession(directory, "local:world|player:one", ".networks");
        assertNotEquals(main, networks);
        assertTrue(networks.getFileName().toString().endsWith(".networks.json"));
    }

    @Test void missingCacheIsEmptyAndCorruptOrFutureCacheIsRejected() throws Exception {
        Path file = directory.resolve("cache.json");
        assertTrue(CacheFile.read(file).isEmpty());
        Files.writeString(file, "{not valid json");
        assertThrows(IOException.class, () -> CacheFile.read(file));
        Files.writeString(file, "{\"version\":999,\"containers\":[]}");
        assertThrows(IOException.class, () -> CacheFile.read(file));
        Files.writeString(file, "{\"version\":1,\"containers\":[null]}");
        assertThrows(IOException.class, () -> CacheFile.read(file));
    }

    @Test void rejectsMalformedRecordWithoutCrashingTheClient() throws Exception {
        Path file = directory.resolve("cache.json");
        Files.writeString(file, "{\"version\":1,\"containers\":[{\"dimension\":\"minecraft:overworld\"}]}");
        assertThrows(IOException.class, () -> CacheFile.read(file));
    }
}
