package dev.findmeclientside.client;

import dev.findmeclientside.ClientConfig;
import dev.findmeclientside.FindMeClientside;
import dev.findmeclientside.cache.BlockAnchor;
import dev.findmeclientside.cache.CacheFile;
import dev.findmeclientside.cache.ContainerCache;
import dev.findmeclientside.cache.ItemSnapshot;
import dev.findmeclientside.cache.RememberedContainer;
import dev.findmeclientside.compat.AeCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class ContainerTracker {
    public static final ContainerTracker INSTANCE = new ContainerTracker();
    public static final String NETWORK = "me_network";
    // Cache files can reach megabytes. Write them off the render thread, one at a time and in order.
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Find Me Clientside cache writer");
        thread.setDaemon(true);
        return thread;
    });
    private static ResourceKey<Level> dimensionKey;
    private static String dimensionId;
    private final Store containers = new Store("");
    // AE2 networks can hold 100,000+ entries and change rarely, so they live in their own file and
    // are not rewritten every time a chest changes.
    private final Store networks = new Store(".networks");
    private String session;
    private ClientPacketListener sessionConnection;
    private LocalPlayer sessionPlayer;
    private Pending pending;
    private Active active;
    private RememberedContainer lastSnapshot;
    private ItemStack[] seenStacks = new ItemStack[0];
    private CompoundTag[] seenTags = new CompoundTag[0];
    private int[] seenCounts = new int[0];
    private long lastSave;
    private record Pending(String dimension, List<BlockAnchor> anchors, String kind, long clickedAt) {}
    /**
     * @param slots     whether the menu's slots belong to the target block (false for wireless terminals,
     *                  whose target is the linked access point)
     * @param dimension where the menu was opened; a wireless terminal's access point may be elsewhere
     */
    private record Active(AbstractContainerMenu menu, Pending target, String title, boolean slots, String dimension) {}

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
        // A wireless terminal has no clicked block; its network is anchored at the linked access point.
        // Checked first, so a block clicked just before opening the terminal is not mistaken for it.
        Pending target = ClientEvents.ae2() ? accessPoint(menu) : null;
        boolean slots = target == null;
        if (slots) {
            if (pending == null || System.currentTimeMillis() - pending.clickedAt > 5000
                    || !pending.dimension.equals(dimension())) return;
            target = pending;
        }
        String title = mc.screen instanceof AbstractContainerScreen<?> screen
                ? screen.getTitle().getString() : mc.level.getBlockState(position(target.anchors.get(0)))
                .getBlock().getName().getString();
        active = new Active(menu, target, title, slots, dimension());
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
            if (!active.dimension.equals(dimension())) {
                active = null;
            } else if (mc.player.containerMenu != active.menu) {
                snapshot();
                rememberNetwork(true);
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
            if (!stored(slot)) continue;
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
        if (active == null || !active.slots || mc.player == null || mc.level == null
                || !active.target.dimension.equals(dimension())) return;
        List<ItemSnapshot> items = new ArrayList<>();
        for (Slot slot : active.menu.slots) {
            ItemStack stack = slot.getItem();
            if (!stored(slot) || stack.isEmpty()) continue;
            items.add(item(stack));
            // Buckets, cells and jugs count toward fluid searches too.
            SearchQuery.containedFluid(stack)
                    .ifPresent(fluid -> items.add(fluid(fluid, (long) fluid.getAmount() * stack.getCount())));
        }
        // Tanks are not menu slots. Reading the opened block's fluid capability also captures mods that sync
        // tank contents only while their screen is open, such as GregTech fluid hatches.
        for (BlockAnchor anchor : active.target.anchors) {
            BlockEntity entity = mc.level.getBlockEntity(position(anchor));
            if (entity != null) tanks(entity, items);
        }
        RememberedContainer next = new RememberedContainer(active.target.dimension, active.title,
                active.target.kind, active.target.anchors, List.copyOf(items), System.currentTimeMillis());
        RememberedContainer previous = lastSnapshot;
        // Refresh observation time once a second while open; avoid disk writes for every client tick.
        if (previous == null || !previous.items().equals(next.items())
                || !previous.anchors().equals(next.anchors()) || next.observedAt() - previous.observedAt() >= 1000) {
            containers.cache.remember(next);
            lastSnapshot = next;
        }
    }

    /**
     * Records an AE2 terminal's network, anchored at the terminal block. The entry list is copied on this
     * thread (references only); converting it to cache data runs on the worker so huge networks never hitch.
     */
    private void rememberNetwork(boolean async) {
        if (!ClientEvents.ae2() || active == null) return;
        Supplier<List<ItemSnapshot>> contents = AeCompat.networkContents(active.menu);
        if (contents == null) return;
        Pending target = active.target;
        String title = active.title;
        String owner = session;
        Supplier<RememberedContainer> record = () -> new RememberedContainer(target.dimension, title, NETWORK,
                target.anchors, contents.get(), System.currentTimeMillis());
        if (!async) {
            networks.cache.remember(record.get());
            return;
        }
        WORKER.execute(() -> {
            RememberedContainer network = record.get();
            Minecraft.getInstance().execute(() -> {
                if (owner.equals(session)) networks.cache.remember(network);
            });
        });
    }

    /** The open AE2 terminal's block or linked access point, if it is in this dimension. */
    public List<BlockAnchor> anchorsOf(AbstractContainerMenu menu) {
        return active != null && active.menu == menu && active.target.dimension.equals(dimension())
                ? active.target.anchors : List.of();
    }

    private static Pending accessPoint(AbstractContainerMenu menu) {
        GlobalPos linked = AeCompat.linkedAccessPoint(menu);
        if (linked == null) return null;
        Level level = Minecraft.getInstance().level;
        String dimension = linked.dimension().location().toString();
        BlockPos pos = linked.pos();
        // The block ID lets a later search drop the network once the access point is gone; it can only be
        // read while that chunk is loaded here.
        String blockId = dimension.equals(dimension()) && level.isLoaded(pos)
                ? BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString() : "ae2:wireless_access_point";
        return new Pending(dimension, List.of(new BlockAnchor(pos.getX(), pos.getY(), pos.getZ(), blockId)),
                "block", System.currentTimeMillis());
    }

    public List<RememberedContainer> search(SearchQuery query) {
        Minecraft mc = Minecraft.getInstance();
        if (!ensureSession() || mc.player == null || mc.level == null) return List.of();
        snapshot();
        prune();
        List<RememberedContainer> found = new ArrayList<>();
        for (Store store : List.of(containers, networks)) {
            // A loaded block that has changed type can no longer support this remembered inventory.
            store.cache.removeIf(c -> c.dimension().equals(dimension()) && c.anchors().stream().anyMatch(a -> {
                BlockPos pos = position(a);
                return mc.level.hasChunkAt(pos)
                        && !BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString().equals(a.blockId());
            }));
            found.addAll(store.cache.search(dimension(), mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    ClientConfig.SEARCH_RADIUS.get(), query.snapshots(), ClientConfig.MATCH_NBT.get()));
        }
        return found;
    }

    public static ItemSnapshot item(ItemStack stack) {
        return new ItemSnapshot(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                stack.hasTag() ? stack.getTag().toString() : "", stack.getCount());
    }

    public static ItemSnapshot fluid(FluidStack stack) { return fluid(stack, stack.getAmount()); }

    private static ItemSnapshot fluid(FluidStack stack, long amount) {
        return new ItemSnapshot(BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString(),
                stack.hasTag() ? stack.getTag().toString() : "", Math.max(1, amount), true);
    }

    private static void tanks(BlockEntity entity, List<ItemSnapshot> items) {
        try {
            IFluidHandler handler = entity.getCapability(ForgeCapabilities.FLUID_HANDLER, null).resolve().orElse(null);
            if (handler == null) return;
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack stack = handler.getFluidInTank(tank);
                if (!stack.isEmpty()) items.add(fluid(stack));
            }
        } catch (RuntimeException failure) {
            FindMeClientside.LOGGER.debug("Could not read client-side tanks of {}", entity.getType(), failure);
        }
    }

    /** Player slots, terminal grids and filter ghosts must never be attributed to the opened block. */
    private static boolean stored(Slot slot) {
        return !(slot.container instanceof Inventory) && !(ClientEvents.ae2() && AeCompat.isVirtual(slot));
    }

    public int size() { ensureSession(); return containers.cache.size() + networks.cache.size(); }

    public void clear() {
        if (!ensureSession()) return;
        active = null;
        pending = null;
        containers.cache.clear();
        networks.cache.clear();
        save(false);
        SearchController.clear();
    }

    public void disconnect() {
        snapshot();
        rememberNetwork(false);
        // Wait here so quitting the game cannot cut off the final write.
        save(true);
        active = null;
        pending = null;
        for (Store store : List.of(containers, networks)) store.close();
        session = null;
        sessionConnection = null;
        sessionPlayer = null;
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
        SearchController.clear();
        session = nextSession;
        Path directory = mc.gameDirectory.toPath().resolve("findmeclientside");
        for (Store store : List.of(containers, networks)) store.open(directory, session);
        prune();
        lastSave = System.currentTimeMillis();
        return true;
    }

    private void prune() {
        long oldest = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(ClientConfig.MAX_AGE_DAYS.get());
        for (Store store : List.of(containers, networks)) store.cache.prune(oldest, ClientConfig.MAX_CONTAINERS.get());
    }

    private void save(boolean wait) {
        lastSave = System.currentTimeMillis();
        boolean pruned = false;
        for (Store store : List.of(containers, networks)) {
            if (!store.needsSave()) continue;
            if (!pruned) prune();
            pruned = true;
            store.save();
        }
        if (wait) for (Store store : List.of(containers, networks)) store.awaitWrite();
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

    /** One cache and its file, saved in the background. */
    private static final class Store {
        final ContainerCache cache = new ContainerCache();
        private final String suffix;
        private Path file;
        private Future<?> lastWrite = CompletableFuture.completedFuture(null);
        private volatile Path failedWrite;

        Store(String suffix) { this.suffix = suffix; }

        void open(Path directory, String session) {
            cache.clear();
            cache.markSaved();
            file = CacheFile.forSession(directory, session, suffix);
            if (!ClientConfig.PERSIST_CACHE.get()) return;
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

        void close() {
            cache.clear();
            cache.markSaved();
            file = null;
        }

        boolean needsSave() {
            return file != null && (cache.isDirty() || file.equals(failedWrite)) && ClientConfig.PERSIST_CACHE.get();
        }

        void save() {
            // Entries are immutable records, so the copy can be serialized safely on the worker thread.
            Path target = file;
            List<RememberedContainer> entries = cache.entries();
            cache.markSaved();
            failedWrite = null;
            lastWrite = WORKER.submit(() -> {
                try {
                    CacheFile.write(target, entries);
                } catch (IOException failure) {
                    failedWrite = target;
                    FindMeClientside.LOGGER.warn("Could not save container cache {}", target, failure);
                }
            });
        }

        void awaitWrite() {
            try {
                lastWrite.get();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException failure) {
                FindMeClientside.LOGGER.warn("Container cache writer failed", failure);
            }
        }
    }

    private ContainerTracker() {}
}
