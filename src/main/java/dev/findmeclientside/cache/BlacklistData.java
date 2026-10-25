package dev.findmeclientside.cache;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The block search blacklist as block IDs: generated defaults plus the player's own changes. Changes are
 * stored separately from the defaults, so regenerating after a mod list change keeps them.
 */
public final class BlacklistData {
    private static final Gson GSON = new Gson();
    private static final int VERSION = 1;
    private record Document(int version, String modList, List<String> defaults, List<String> added, List<String> removed) {}

    private String modList = "";
    private final Set<String> defaults = new TreeSet<>();
    private final Set<String> added = new TreeSet<>();
    private final Set<String> removed = new TreeSet<>();

    public boolean contains(String id) { return added.contains(id) || defaults.contains(id) && !removed.contains(id); }
    public boolean isDefault(String id) { return defaults.contains(id); }
    public boolean isChanged(String id) { return added.contains(id) || removed.contains(id); }
    public boolean generated() { return !modList.isEmpty(); }
    public String modList() { return modList; }

    public void set(String id, boolean blacklisted) {
        added.remove(id);
        removed.remove(id);
        if (blacklisted != defaults.contains(id)) (blacklisted ? added : removed).add(id);
    }

    public void resetToDefaults() {
        added.clear();
        removed.clear();
    }

    public void regenerate(String modList, Collection<String> generated) {
        this.modList = modList;
        defaults.clear();
        defaults.addAll(generated);
        // Changes that now agree with the defaults are no longer changes.
        added.removeAll(defaults);
        removed.retainAll(defaults);
    }

    public Set<String> effective() {
        Set<String> effective = new TreeSet<>(defaults);
        effective.removeAll(removed);
        effective.addAll(added);
        return effective;
    }

    public static BlacklistData read(Path file) throws IOException {
        BlacklistData data = new BlacklistData();
        if (!Files.exists(file)) return data;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Document document = GSON.fromJson(reader, Document.class);
            if (document == null || document.version != VERSION) throw new IOException("Unsupported blacklist document");
            data.modList = document.modList == null ? "" : document.modList;
            if (document.defaults != null) data.defaults.addAll(document.defaults);
            if (document.added != null) data.added.addAll(document.added);
            if (document.removed != null) data.removed.addAll(document.removed);
            return data;
        } catch (RuntimeException failure) {
            throw new IOException("Invalid blacklist document", failure);
        }
    }

    public void write(Path file) throws IOException {
        CacheFile.writeJson(file, new Document(VERSION, modList, List.copyOf(defaults), List.copyOf(added), List.copyOf(removed)));
    }
}
