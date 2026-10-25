package dev.findmeclientside.client;

import dev.findmeclientside.FindMeClientside;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Searches the chunks the client has loaded: placed blocks, and block entities whose item or fluid
 * capabilities the server syncs to clients (GregTech quantum chests/tanks and machine inventories,
 * TerraFirmaCraft barrels, ...). Chunks are visited nearest first under a per-tick time budget, so even
 * a search for a common block never stalls a frame.
 */
final class WorldScan {
    static final int MAX_PLACED = 512;
    static final int MAX_INVENTORIES = 512;
    private static final long BUDGET_NANOS = 2_000_000;
    // Block entity types whose client-side capabilities have thrown once; skipped for the rest of the session.
    private static final Set<BlockEntityType<?>> BROKEN = new HashSet<>();

    /** A loaded block entity whose synced contents match: item count and fluid amount in mB. */
    record Inventory(BlockPos pos, long items, long fluid) {}

    private final ClientLevel level;
    private final SearchQuery query;
    private final boolean matchNbt;
    private final double x, y, z, radiusSquared;
    private final int minY, maxY;
    private final List<ChunkPos> chunks;
    private final boolean placed, inventories, entityBlocksOnly;
    private final Predicate<BlockState> isTarget;
    private final List<BlockPos> placedFound = new ArrayList<>();
    private final List<Inventory> inventoriesFound = new ArrayList<>();
    // Every block entity that reported synced contents, matching or not. Their live view replaces remembered snapshots.
    private final Set<BlockPos> synced = new HashSet<>();
    private int next;

    /**
     * @param blocks placed blocks to find, already filtered by the blacklist; empty to skip block search
     */
    WorldScan(ClientLevel level, SearchQuery query, double x, double y, double z, int radius,
              boolean matchNbt, Set<Block> blocks, boolean searchInventories) {
        this.level = level;
        this.query = query;
        this.matchNbt = matchNbt;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radiusSquared = (double) radius * radius;
        this.minY = (int) Math.floor(y) - radius;
        this.maxY = (int) Math.floor(y) + radius;
        this.placed = !blocks.isEmpty();
        this.inventories = searchInventories && (query.hasItems() || query.hasFluids());
        this.entityBlocksOnly = blocks.stream().allMatch(block -> block instanceof EntityBlock);
        // Only fluid sources count; flowing fluid would outline every block of a stream.
        this.isTarget = state -> blocks.contains(state.getBlock())
                && (!(state.getBlock() instanceof LiquidBlock) || state.getFluidState().isSource());
        this.chunks = placed || inventories ? loadedChunks(radius) : List.of();
    }

    boolean done() { return next >= chunks.size(); }
    List<BlockPos> placed() { return placedFound; }
    List<Inventory> inventories() { return inventoriesFound; }
    Set<BlockPos> synced() { return synced; }
    boolean placedCapped() { return placedFound.size() >= MAX_PLACED; }

    /** Scans chunks until the time budget runs out. Returns true if anything new was found. */
    boolean step() {
        long deadline = System.nanoTime() + BUDGET_NANOS;
        int before = placedFound.size() + inventoriesFound.size();
        while (!done() && System.nanoTime() < deadline) {
            LevelChunk chunk = level.getChunkSource().getChunk(chunks.get(next).x, chunks.get(next).z, ChunkStatus.FULL, false);
            next++;
            if (chunk == null) continue;
            // Placed-block positions also come from block entities when every target has one, which
            // skips reading 4096 states per section.
            if (placed && !placedCapped() && !entityBlocksOnly) scanSections(chunk);
            if (inventories || placed && entityBlocksOnly) scanBlockEntities(chunk);
            if (placedCapped() && !inventories) next = chunks.size();
        }
        return placedFound.size() + inventoriesFound.size() != before;
    }

    private void scanSections(LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length && !placedCapped(); i++) {
            LevelChunkSection section = sections[i];
            int bottom = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            if (bottom > maxY || bottom + 15 < minY || section.hasOnlyAir()) continue;
            // The palette check rejects most sections without touching their 4096 block states.
            if (!section.maybeHas(isTarget)) continue;
            int baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();
            for (int ly = 0; ly < 16; ly++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        if (!isTarget.test(section.getBlockState(lx, ly, lz))) continue;
                        int bx = baseX + lx, by = bottom + ly, bz = baseZ + lz;
                        if (within(bx, by, bz)) {
                            placedFound.add(new BlockPos(bx, by, bz));
                            if (placedCapped()) return;
                        }
                    }
                }
            }
        }
    }

    private void scanBlockEntities(LevelChunk chunk) {
        for (BlockEntity entity : chunk.getBlockEntities().values()) {
            BlockPos pos = entity.getBlockPos();
            if (!within(pos.getX(), pos.getY(), pos.getZ())) continue;
            if (placed && entityBlocksOnly && !placedCapped() && isTarget.test(entity.getBlockState())) placedFound.add(pos);
            if (inventories && inventoriesFound.size() < MAX_INVENTORIES && !BROKEN.contains(entity.getType())) readContents(entity);
        }
    }

    private void readContents(BlockEntity entity) {
        long items = 0, fluid = 0;
        boolean any = false;
        try {
            if (query.hasItems()) {
                IItemHandler handler = entity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).resolve().orElse(null);
                if (handler != null) {
                    for (int slot = 0; slot < handler.getSlots(); slot++) {
                        var stack = handler.getStackInSlot(slot);
                        if (stack.isEmpty()) continue;
                        any = true;
                        if (query.matches(stack, matchNbt)) items += stack.getCount();
                    }
                }
            }
            if (query.hasFluids()) {
                IFluidHandler handler = entity.getCapability(ForgeCapabilities.FLUID_HANDLER, null).resolve().orElse(null);
                if (handler != null) {
                    for (int tank = 0; tank < handler.getTanks(); tank++) {
                        FluidStack stack = handler.getFluidInTank(tank);
                        if (stack.isEmpty()) continue;
                        any = true;
                        if (query.matches(stack, matchNbt)) fluid += stack.getAmount();
                    }
                }
            }
        } catch (RuntimeException failure) {
            // Some mods expect capabilities to be read only on the server.
            BROKEN.add(entity.getType());
            FindMeClientside.LOGGER.debug("Skipping client-side contents of {}", entity.getType(), failure);
            return;
        }
        // Clients usually receive empty inventories for unopened blocks, so only content proves the view is synced.
        if (any) synced.add(entity.getBlockPos());
        if (items > 0 || fluid > 0) inventoriesFound.add(new Inventory(entity.getBlockPos(), items, fluid));
    }

    private boolean within(int bx, int by, int bz) {
        double dx = bx + 0.5 - x, dy = by + 0.5 - y, dz = bz + 0.5 - z;
        return dx * dx + dy * dy + dz * dz <= radiusSquared;
    }

    private List<ChunkPos> loadedChunks(int radius) {
        int centerX = SectionPos.blockToSectionCoord(x), centerZ = SectionPos.blockToSectionCoord(z);
        int range = SectionPos.blockToSectionCoord(radius) + 1;
        List<ChunkPos> loaded = new ArrayList<>();
        for (int cx = centerX - range; cx <= centerX + range; cx++) {
            for (int cz = centerZ - range; cz <= centerZ + range; cz++) {
                if (level.getChunkSource().hasChunk(cx, cz) &&nearestDistanceSquared(cx, cz) <= radiusSquared) loaded.add(new ChunkPos(cx, cz));
            }
        }
        loaded.sort(Comparator.comparingDouble(pos -> nearestDistanceSquared(pos.x, pos.z)));
        return Collections.unmodifiableList(loaded);
    }

    private double nearestDistanceSquared(int cx, int cz) {
        double dx = Math.max(0, Math.max(cx * 16 - x, x - (cx * 16 + 16)));
        double dz = Math.max(0, Math.max(cz * 16 - z, z - (cz * 16 + 16)));
        return dx * dx + dz * dz;
    }
}
