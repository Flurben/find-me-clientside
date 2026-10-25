package dev.findmeclientside.client;

import dev.findmeclientside.ClientConfig;
import dev.findmeclientside.FindMeClientside;
import dev.findmeclientside.cache.BlockAnchor;
import dev.findmeclientside.cache.CacheFile;
import dev.findmeclientside.cache.ContainerCache;
import dev.findmeclientside.cache.ItemSnapshot;
import dev.findmeclientside.cache.RememberedContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class ContainerTracker {
    public static final ContainerTracker INSTANCE = new ContainerTracker();
    // Cache files can reach megabytes. Write them off the render thread, one at a time and in order.
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Find Me Clientside cache writer");
        thread.setDaemon(true);
        return thread;
    });
    private static ResourceKey<Level> dimensionKey;
    private static String dimensionId;
    private final ContainerCache cache = new ContainerCache();
    private String session;
    private ClientPacketListener sessionConnection;
    private LocalPlayer sessionPlayer;
    private Path file;
    private Pending pending;
    private Active active;
    private RememberedContainer lastSnapshot;
    private ItemStack[] seenStacks = new ItemStack[0];
    private CompoundTag[] seenTags = new CompoundTag[0];
    private int[] seenCounts = new int[0];
    private long lastSave;
    private Future<?> lastWrite = CompletableFuture.completedFuture(null);
    private volatile Path failedWrite;
    private record Pending(String dimension, List<BlockAnchor> anchors, String kind, long clickedAt) {}
    private record Active(AbstractContainerMenu menu, Pending target, String title) {}

    public void clicked(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (!ensureSession() || mc.level == null) return;
        pending = null;
        if (mc.level.getBlockEntity(pos) == null) return;
        BlockState state = mc.level.getBlockState(pos);
        List<BlockAnchor> anchors = new ArrayList<>();
        anchors.add(anchor(pos, state));
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)
                && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos neighbor = pos.relative(ChestBlock.getConnectedDirection(state));
            BlockState neighborState = mc.level.getBlockState(neighbor);
            if (neighborState.is(state.getBlock()) && neighborState.hasProperty(ChestBlock.TYPE)
                    && neighborState.getValue(ChestBlock.TYPE) != ChestType.SINGLE
                    && neighbor.relative(ChestBlock.getConnectedDirection(neighborState)).equals(pos)) {
                anchors.add(anchor(neighbor, neighborState));
            }
        }
        pending = new Pending(dimension(), List.copyOf(anchors),
                state.getBlock() instanceof EnderChestBlock ? "ender_chest" : "block", System.currentTimeMillis());
    }

    public void receivedContents(AbstractContainerMenu menu) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || menu == mc.player.inventoryMenu
                || menu != mc.player.containerMenu || !ensureSession()) return;
        if (active != null && active.menu == menu) {
            slotsChanged();
            snapshot();
            return;
        }
        if (pending == null || System.currentTimeMillis() - pending.clickedAt > 5000
                || !pending.dimension.equals(dimension())) return;
        String title = mc.screen instanceof AbstractContainerScreen<?> screen
                ? screen.getTitle().getString() : mc.level.getBlockState(position(pending.anchors.get(0)))
                .getBlock().getName().getString();
        active = new Active(menu, pending, title);
        lastSnapshot = null;
        pending = null;
        seenStacks = new ItemStack[0];
        slotsChanged();
        // This hook runs only after the server's initial contents have arrived, including empty containers.
        snapshot();
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (!ensureSession()) return;
        if (active != null) {
            if (!active.target.dimension.equals(dimension())) {
                active = null;
            } else if (mc.player.containerMenu != active.menu) {
                snapshot();
                active = null;
            } else if (slotsChanged()) {
                snapshot();
            }
        }
        if (pending != null && (System.currentTimeMillis() - pending.clickedAt > 5000
                || !pending.dimension.equals(dimension()))) pending = null;
        if (System.currentTimeMillis() - lastSave >= 5000) save(false);
    }

    // Runs every tick, so it compares slot references instead of serializing item NBT. Server updates
    // and clicks replace stacks, counts or tags; an NBT edit made in place is still recorded by the
    // full snapshot taken on close, on search and on disconnect.
    private boolean slotsChanged() {
        List<Slot> slots = active.menu.slots;
        if (seenStacks.length != slots.size()) {
            seenStacks = new ItemStack[slots.size()];
            seenTags = new CompoundTag[slots.size()];
            seenCounts = new int[slots.size()];
        }
        boolean changed = false;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.container instanceof Inventory) continue;
            ItemStack stack = slot.getItem();
            if (stack != seenStacks[i] || stack.getCount() != seenCounts[i] || stack.getTag() != seenTags[i]) {
                seenStacks[i] = stack;
                seenTags[i] = stack.getTag();
                seenCounts[i] = stack.getCount();
                changed = true;
            }
        }
        return changed;
    }

    public void snapshot() {
        Minecraft mc = Minecraft.getInstance();
        if (active == null || mc.player == null || mc.level == null
                || !active.target.dimension.equals(dimension())) return;
        List<ItemSnapshot> items = new ArrayList<>();
        // Player slots must never be attributed to the chest, furnace or modded storage block.
        active.menu.slots.stream().filter(slot -> !(slot.container instanceof Inventory))
                .map(slot -> slot.getItem()).filter(stack -> !stack.isEmpty())
                .forEach(stack -> items.add(item(stack)));
        RememberedContainer next = new RememberedContainer(active.target.dimension, active.title,
                active.target.kind, active.target.anchors, List.copyOf(items), System.currentTimeMillis());
        RememberedContainer previous = lastSnapshot;
        // Refresh observation time once a second while open; avoid disk writes for every client tick.
        if (previous == null || !previous.items().equals(next.items())
                || !previous.anchors().equals(next.anchors()) || next.observedAt() - previous.observedAt() >= 1000) {
            cache.remember(next);
            lastSnapshot = next;
        }
    }

    public List<RememberedContainer> search(List<ItemStack> stacks) {
        Minecraft mc = Minecraft.getInstance();
        if (!ensureSession() || mc.player == null || mc.level == null) return List.of();
        snapshot();
        prune();
        // A loaded block that has changed type can no longer support this remembered inventory.
        cache.removeIf(c -> c.dimension().equals(dimension()) && c.anchors().stream().anyMatch(a -> {
            BlockPos pos = position(a);
            return mc.level.hasChunkAt(pos)
                    && !BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString().equals(a.blockId());
        }));
        return cache.search(dimension(), mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                ClientConfig.SEARCH_RADIUS.get(), queries(stacks), ClientConfig.MATCH_NBT.get());
    }

    public static List<ItemSnapshot> queries(List<ItemStack> stacks) {
        Map<String, ItemSnapshot> unique = new LinkedHashMap<>();
        stacks.stream().filter(s -> !s.isEmpty()).map(ContainerTracker::item)
                .forEach(i -> unique.put(i.itemId() + "\n" + i.tag(), i));
        return List.copyOf(unique.values());
    }

    public static ItemSnapshot item(ItemStack stack) {
        return new ItemSnapshot(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                stack.hasTag() ? stack.getTag().toString() : "", stack.getCount());
    }

    public int size() { ensureSession(); return cache.size(); }

    public void clear() {
        if (!ensureSession()) return;
        active = null;
        pending = null;
        cache.clear();
        save(false);
        SearchController.clear();
    }

    public void disconnect() {
        snapshot();
        // Wait here so quitting the game cannot cut off the final write.
        save(true);
        active = null;
        pending = null;
        cache.clear();
        cache.markSaved();
        session = null;
        sessionConnection = null;
        sessionPlayer = null;
        file = null;
        SearchController.clear();
    }

    private boolean ensureSession() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        // The identity only changes with a new connection or player, so skip rebuilding it every tick.
        if (session != null && mc.getConnection() == sessionConnection && mc.player == sessionPlayer) return true;
        String identity;
        if (mc.getSingleplayerServer() != null) {
            identity = "local:" + mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        } else if (mc.getCurrentServer() != null) {
            identity = "server:" + mc.getCurrentServer().ip.toLowerCase(Locale.ROOT);
        } else if (mc.getConnection() != null) {
            identity = "connection:" + mc.getConnection().getConnection().getRemoteAddress();
        } else return false;
        String nextSession = identity + "|player:" + mc.player.getUUID();
        sessionConnection = mc.getConnection();
        sessionPlayer = mc.player;
        if (nextSession.equals(session)) return true;
        save(false);
        active = null;
        pending = null;
        cache.clear();
        cache.markSaved();
        SearchController.clear();
        session = nextSession;
        file = CacheFile.forSession(mc.gameDirectory.toPath().resolve("findmeclientside"), session);
        if (ClientConfig.PERSIST_CACHE.get()) {
            awaitWrite();
            try {
                cache.load(CacheFile.read(file));
            } catch (IOException failure) {
                FindMeClientside.LOGGER.warn("Could not read container cache {}", file, failure);
                // Preserve damaged or future-version files instead of overwriting them.
                try {
                    if (Files.exists(file)) Files.move(file, file.resolveSibling(file.getFileName()
                            + ".unreadable-" + System.currentTimeMillis()));
                } catch (IOException backupFailure) {
                    FindMeClientside.LOGGER.warn("Could not preserve unreadable cache; disk saves disabled for this session", backupFailure);
                    file = null;
                }
            }
        }
        prune();
        lastSave = System.currentTimeMillis();
        return true;
    }

    private void prune() {
        cache.prune(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(ClientConfig.MAX_AGE_DAYS.get()),
                ClientConfig.MAX_CONTAINERS.get());
    }

    private void save(boolean wait) {
        lastSave = System.currentTimeMillis();
        boolean retry = file != null && file.equals(failedWrite);
        if (file != null && (cache.isDirty() || retry) && ClientConfig.PERSIST_CACHE.get()) {
            prune();
            // Entries are immutable records, so the copy can be serialized safely on the writer thread.
            Path target = file;
            List<RememberedContainer> entries = cache.entries();
            cache.markSaved();
            failedWrite = null;
            lastWrite = WRITER.submit(() -> {
                try {
                    CacheFile.write(target, entries);
                } catch (IOException failure) {
                    failedWrite = target;
                    FindMeClientside.LOGGER.warn("Could not save container cache {}", target, failure);
                }
            });
        }
        if (wait) awaitWrite();
    }

    private void awaitWrite() {
        try {
            lastWrite.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException failure) {
            FindMeClientside.LOGGER.warn("Container cache writer failed", failure);
        }
    }

    public static String dimension() {
        // A dimension's key is interned, so build its ID string only when the dimension changes.
        ResourceKey<Level> key = Minecraft.getInstance().level.dimension();
        if (key != dimensionKey) {
            dimensionKey = key;
            dimensionId = key.location().toString();
        }
        return dimensionId;
    }
    public static BlockPos position(BlockAnchor a) { return new BlockPos(a.x(), a.y(), a.z()); }
    private static BlockAnchor anchor(BlockPos pos, BlockState state) {
        return new BlockAnchor(pos.getX(), pos.getY(), pos.getZ(), BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    }

    private ContainerTracker() {}
}
