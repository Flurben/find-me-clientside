package dev.findmeclientside.client;

import dev.findmeclientside.ClientConfig;
import dev.findmeclientside.cache.BlockAnchor;
import dev.findmeclientside.cache.QuerySet;
import dev.findmeclientside.cache.RememberedContainer;
import dev.findmeclientside.compat.AeCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class SearchController {
    public static final int CONTAINER = 0x26E6FF, PLACED = 0xFFB33D, NETWORK = 0xB98CFF;
    private static final long NOTICE_MILLIS = 3000;
    // Outlines for containers and networks; placed blocks are capped separately by the scan.
    private static final int MAX_CONTAINER_BOXES = 256;

    /** One outlined block; built when results change, not every frame. */
    public record Highlight(BlockPos pos, int color) {}

    private static SearchQuery query;
    private static boolean blockSearch;
    private static List<RememberedContainer> remembered = List.of();
    private static long[] liveNetwork;
    private static List<BlockAnchor> liveNetworkAnchors = List.of();
    private static WorldScan scan;
    private static List<Highlight> highlights = List.of();
    // Slot backgrounds are drawn every frame; remember which stacks hold a searched fluid.
    private static final Map<ItemStack, Boolean> containsFluid = new IdentityHashMap<>();
    private static List<String> lines = List.of();
    private static List<String> noticeLines = List.of();
    private static boolean closeOnResult;
    private static Screen searchScreen;
    private static String dimension;
    private static long expiresAt;
    private static long noticeExpiresAt;

    /** Finds the query in containers, synced inventories and AE2 networks. */
    public static boolean search(SearchQuery next) { return search(next, false); }

    /**
     * @param blocks find the query's placed blocks and fluid sources instead of where the items are stored
     */
    public static boolean search(SearchQuery next, boolean blocks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || next == null) return false;
        Set<Block> targets = Set.of();
        if (blocks) {
            if (next.blocks().isEmpty()) {
                notice(Component.translatable("findmeclientside.not_block", next.name()));
                return true;
            }
            targets = new HashSet<>(next.blocks());
            targets.removeIf(BlockBlacklist::contains);
            if (targets.isEmpty()) {
                notice(Component.translatable("findmeclientside.blacklisted", next.name()));
                return true;
            }
        }
        boolean matchNbt = ClientConfig.MATCH_NBT.get();
        query = next;
        blockSearch = blocks;
        containsFluid.clear();
        remembered = blocks ? List.of() : ContainerTracker.INSTANCE.search(next);
        liveNetwork = !blocks && ClientEvents.ae2() ? AeCompat.liveTotals(mc.player.containerMenu, next, matchNbt) : null;
        liveNetworkAnchors = blocks ? List.of() : ContainerTracker.INSTANCE.anchorsOf(mc.player.containerMenu);
        scan = new WorldScan(mc.level, next, mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                ClientConfig.SEARCH_RADIUS.get(), matchNbt, targets, !blocks && ClientConfig.LOADED_INVENTORIES.get());
        scan.step();
        dimension = ContainerTracker.dimension();
        expiresAt = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(ClientConfig.HIGHLIGHT_SECONDS.get());
        noticeExpiresAt = 0;
        closeOnResult = ClientConfig.CLOSE_SCREEN.get();
        searchScreen = mc.screen;
        refresh();
        return true;
    }

    /** Shows a short message in the search panel without changing the current highlights. */
    public static void notice(Component message) {
        noticeLines = List.of(message.getString());
        noticeExpiresAt = System.currentTimeMillis() + NOTICE_MILLIS;
    }

    public static boolean visible() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null && System.currentTimeMillis() < expiresAt
                && ContainerTracker.dimension().equals(dimension);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (dimension != null && (mc.level == null || !dimension.equals(ContainerTracker.dimension())
                || System.currentTimeMillis() >= expiresAt)) clear();
        if (scan != null && !scan.done()) {
            boolean found = scan.step();
            // Rebuild only on news, plus once at the end to drop the "searching" marker.
            if (found || scan.done()) refresh();
        }
    }

    public static List<Highlight> highlights() { return visible() ? highlights : List.of(); }

    /** The search panel's text, title first, or an empty list when nothing should be shown. */
    public static List<String> panelLines() {
        if (System.currentTimeMillis() < noticeExpiresAt) return noticeLines;
        return visible() ? lines : List.of();
    }

    /** Uses the same item, fluid and NBT rules as the container search. Callers check {@link #visible()}. */
    public static boolean matchesItem(ItemStack stack) {
        if (query == null || stack.isEmpty()) return false;
        boolean matchNbt = ClientConfig.MATCH_NBT.get();
        if (query.matches(stack, matchNbt)) return true;
        if (!query.hasFluids()) return false;
        if (containsFluid.size() > 1024) containsFluid.clear();
        return containsFluid.computeIfAbsent(stack, s -> SearchQuery.containedFluid(s)
                .filter(fluid -> query.matches(fluid, matchNbt)).isPresent());
    }

    public static void clear() {
        query = null;
        remembered = List.of();
        liveNetwork = null;
        liveNetworkAnchors = List.of();
        scan = null;
        searchScreen = null;
        highlights = List.of();
        containsFluid.clear();
        lines = List.of();
        noticeExpiresAt = 0;
        expiresAt = 0;
        dimension = null;
    }

    private static void refresh() {
        boolean matchNbt = ClientConfig.MATCH_NBT.get();
        QuerySet snapshots = new QuerySet(query.snapshots());
        Set<BlockPos> synced = scan.synced();
        List<Highlight> boxes = new ArrayList<>();
        Set<BlockPos> outlined = new HashSet<>();
        long items = 0, fluid = 0, networkItems = 0, networkFluid = 0;
        int containers = 0;
        boolean enderCounted = false;
        for (RememberedContainer container : remembered) {
            boolean network = container.kind().equals(ContainerTracker.NETWORK);
            // A synced live view of the same block is newer than any remembered snapshot.
            if (!network && container.anchors().stream().anyMatch(a -> synced.contains(ContainerTracker.position(a)))) continue;
            long itemCount = container.matchingCount(snapshots, matchNbt, false);
            long fluidCount = container.matchingCount(snapshots, matchNbt, true);
            if (network) {
                // Terminals of one network cannot be told apart client-side, so report the largest
                // network instead of adding up every terminal that shows it.
                networkItems = Math.max(networkItems, itemCount);
                networkFluid = Math.max(networkFluid, fluidCount);
            } else if (!container.kind().equals("ender_chest") || !enderCounted) {
                // Every ender chest shows the same inventory; count it once.
                enderCounted |= container.kind().equals("ender_chest");
                items += itemCount;
                fluid += fluidCount;
                containers++;
            } else {
                containers++;
            }
            for (BlockAnchor anchor : container.anchors()) {
                BlockPos pos = ContainerTracker.position(anchor);
                if (boxes.size() < MAX_CONTAINER_BOXES && outlined.add(pos)) boxes.add(new Highlight(pos, network ? NETWORK : CONTAINER));
            }
        }
        for (WorldScan.Inventory inventory : scan.inventories()) {
            items += inventory.items();
            fluid += inventory.fluid();
            containers++;
            if (boxes.size() < MAX_CONTAINER_BOXES && outlined.add(inventory.pos())) boxes.add(new Highlight(inventory.pos(), CONTAINER));
        }
        if (liveNetwork != null && liveNetwork[0] + liveNetwork[1] > 0) {
            networkItems = Math.max(networkItems, liveNetwork[0]);
            networkFluid = Math.max(networkFluid, liveNetwork[1]);
            for (BlockAnchor anchor : liveNetworkAnchors) {
                BlockPos pos = ContainerTracker.position(anchor);
                if (boxes.size() < MAX_CONTAINER_BOXES && outlined.add(pos)) boxes.add(new Highlight(pos, NETWORK));
            }
        }
        for (BlockPos pos : scan.placed()) boxes.add(new Highlight(pos, PLACED));
        highlights = List.copyOf(boxes);

        List<String> parts = new ArrayList<>();
        if (containers > 0) {
            parts.add(Component.translatable(containers == 1 ? "findmeclientside.part.container" : "findmeclientside.part.containers",
                    amount(items, fluid), containers).getString());
        }
        if (networkItems + networkFluid > 0) {
            parts.add(Component.translatable("findmeclientside.part.network", amount(networkItems, networkFluid)).getString());
        }
        if (!scan.placed().isEmpty()) {
            parts.add(Component.translatable("findmeclientside.part.placed",
                    scan.placed().size() + (scan.placedCapped() ? "+" : "")).getString());
        }
        String searching = scan.done() ? "" : " …";
        if (!parts.isEmpty()) {
            lines = List.of(Component.translatable("findmeclientside.found", query.name(), String.join(" · ", parts)).getString() + searching);
            closeScreen();
        } else if (!scan.done()) {
            lines = List.of(Component.translatable("findmeclientside.searching", query.name()).getString());
        } else if (blockSearch) {
            lines = List.of(Component.translatable("findmeclientside.no_placed", query.name(), ClientConfig.SEARCH_RADIUS.get()).getString());
        } else {
            lines = List.of(Component.translatable("findmeclientside.no_matches", query.name(), ClientConfig.SEARCH_RADIUS.get()).getString(),
                    Component.translatable("findmeclientside.hint").getString());
        }
    }

    // Close the inventory or recipe screen once there is something to look at, so the outlines are visible.
    // Results can arrive a few ticks later; never close a screen opened after the search.
    private static void closeScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (!closeOnResult || mc.screen == null || mc.screen != searchScreen || mc.player == null) return;
        closeOnResult = false;
        ContainerTracker.INSTANCE.snapshot();
        if (mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
        mc.setScreen(null);
    }

    private static String amount(long items, long fluid) {
        String itemText = Component.translatable("findmeclientside.amount.items", String.format("%,d", items)).getString();
        String fluidText = Component.translatable("findmeclientside.amount.fluid", String.format("%,d", fluid)).getString();
        return items > 0 && fluid > 0 ? itemText + " + " + fluidText : fluid > 0 ? fluidText : itemText;
    }

    private SearchController() {}
}
