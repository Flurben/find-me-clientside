package dev.findmeclientside.cache;

import java.util.Objects;

public record BlockAnchor(int x, int y, int z, String blockId) {
    public BlockAnchor { Objects.requireNonNull(blockId); }

    public double distanceSquared(double px, double py, double pz) {
        double dx = x + 0.5 - px;
        double dy = y + 0.5 - py;
        double dz = z + 0.5 - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    public boolean samePosition(BlockAnchor other) {
        return x == other.x && y == other.y && z == other.z;
    }
}
