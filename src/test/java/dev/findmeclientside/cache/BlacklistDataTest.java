package dev.findmeclientside.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class BlacklistDataTest {
    @TempDir Path directory;

    @Test void playerChangesOverrideDefaultsAndSurviveRegeneration() {
        BlacklistData data = new BlacklistData();
        data.regenerate("mods-a", List.of("minecraft:stone", "minecraft:dirt"));
        data.set("minecraft:dirt", false);
        data.set("gtceu:lv_macerator", true);
        assertEquals(Set.of("minecraft:stone", "gtceu:lv_macerator"), data.effective());
        data.regenerate("mods-b", List.of("minecraft:stone", "minecraft:dirt", "tfc:rock/raw/granite"));
        assertEquals(Set.of("minecraft:stone", "tfc:rock/raw/granite", "gtceu:lv_macerator"), data.effective());
        assertTrue(data.isChanged("minecraft:dirt"));
    }

    @Test void changesThatMatchTheDefaultsAreNotKept() {
        BlacklistData data = new BlacklistData();
        data.regenerate("mods-a", List.of("minecraft:stone"));
        data.set("minecraft:stone", false);
        data.set("minecraft:stone", true);
        assertFalse(data.isChanged("minecraft:stone"));
        data.set("minecraft:sand", true);
        data.regenerate("mods-b", List.of("minecraft:stone", "minecraft:sand"));
        assertFalse(data.isChanged("minecraft:sand"));
        data.set("minecraft:sand", false);
        data.resetToDefaults();
        assertTrue(data.contains("minecraft:sand"));
    }

    @Test void roundTripsAndRejectsUnknownVersions() throws IOException {
        Path file = directory.resolve("blacklist.json");
        assertFalse(BlacklistData.read(file).generated());
        BlacklistData data = new BlacklistData();
        data.regenerate("mods-a", List.of("minecraft:stone"));
        data.set("minecraft:oak_log", true);
        data.write(file);
        BlacklistData read = BlacklistData.read(file);
        assertEquals("mods-a", read.modList());
        assertEquals(data.effective(), read.effective());
        Files.writeString(file, "{\"version\":99}");
        assertThrows(IOException.class, () -> BlacklistData.read(file));
    }
}
