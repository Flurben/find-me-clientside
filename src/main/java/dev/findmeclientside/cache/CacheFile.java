package dev.findmeclientside.cache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class CacheFile {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int VERSION = 1;
    private record Document(int version, List<RememberedContainer> containers) {}

    public static Path forSession(Path directory, String session) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(session.getBytes(StandardCharsets.UTF_8));
            return directory.resolve(HexFormat.of().formatHex(hash) + ".json");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static List<RememberedContainer> read(Path file) throws IOException {
        if (!Files.exists(file)) return List.of();
        if (Files.size(file) > 32L * 1024 * 1024) throw new IOException("Cache exceeds 32 MiB");
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Document document = GSON.fromJson(reader, Document.class);
            if (document == null || document.version != VERSION || document.containers == null) {
                throw new IOException("Unsupported or incomplete cache document");
            }
            if (document.containers.stream().anyMatch(c -> c == null)) throw new IOException("Null cache entry");
            return List.copyOf(document.containers);
        } catch (RuntimeException failure) {
            throw new IOException("Invalid cache document", failure);
        }
    }

    public static void write(Path file, List<RememberedContainer> containers) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            GSON.toJson(new Document(VERSION, containers), writer);
        }
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private CacheFile() {}
}
