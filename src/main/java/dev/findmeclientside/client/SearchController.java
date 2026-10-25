package dev.findmeclientside.client;

import dev.findmeclientside.ClientConfig;
import dev.findmeclientside.cache.ItemSnapshot;
import dev.findmeclientside.cache.RememberedContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class SearchController {
    private static final long NOTICE_MILLIS = 3000;
    private static List<RememberedContainer> matches = List.of();
    private static List<ItemSnapshot> query = List.of();
    // Tags of the searched stacks by item, for matching slots every frame without serializing NBT.
    private static Map<Item, List<CompoundTag>> targets = Map.of();
    private static List<String> lines = List.of();
    private static List<String> noticeLines = List.of();
    private static String dimension;
    private static long expiresAt;
    private static long noticeExpiresAt;

    public static boolean search(List<ItemStack> stacks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || stacks.isEmpty()) return false;
        matches = ContainerTracker.INSTANCE.search(stacks);
        query = ContainerTracker.queries(stacks);
        Map<Item, List<CompoundTag>> byItem = new HashMap<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) byItem.computeIfAbsent(stack.getItem(), item -> new ArrayList<>()).add(tag(stack));
        }
        targets = byItem;
        dimension = ContainerTracker.dimension();
        expiresAt = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(ClientConfig.HIGHLIGHT_SECONDS.get());
        noticeExpiresAt = 0;
        Component name = stacks.size() == 1 ? stacks.get(0).getHoverName()
                : Component.translatable("findmeclientside.alternatives", stacks.size());
        if (matches.isEmpty()) {
            lines = List.of(Component.translatable("findmeclientside.no_matches", name, ClientConfig.SEARCH_RADIUS.get()).getString(),
                    Component.translatable("findmeclientside.hint").getString());
        } else {
            long count = matches.stream().filter(c -> !c.kind().equals("ender_chest"))
                    .mapToLong(c -> c.matchingCount(query, ClientConfig.MATCH_NBT.get())).sum();
            count += matches.stream().filter(c -> c.kind().equals("ender_chest")).findFirst()
                    .map(c -> c.matchingCount(query, ClientConfig.MATCH_NBT.get())).orElse(0L);
            lines = List.of((matches.size() == 1 ? Component.translatable("findmeclientside.found_one", name, count)
                    : Component.translatable("findmeclientside.found", name, count, matches.size())).getString());
            if (ClientConfig.CLOSE_SCREEN.get() && mc.screen != null) {
                ContainerTracker.INSTANCE.snapshot();
                if (mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
                mc.setScreen(null);
            }
        }
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
    }

    public static List<RememberedContainer> matches() { return visible() ? matches : List.of(); }

    /** The search panel's text, title first, or an empty list when nothing should be shown. */
    public static List<String> panelLines() {
        if (System.currentTimeMillis() < noticeExpiresAt) return noticeLines;
        return visible() ? lines : List.of();
    }

    /** Uses the same item and NBT rules as the container search. Callers check {@link #visible()}. */
    public static boolean matchesItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        List<CompoundTag> tags = targets.get(stack.getItem());
        return tags != null && (!ClientConfig.MATCH_NBT.get() || tags.contains(tag(stack)));
    }

    public static void clear() {
        matches = List.of();
        query = List.of();
        targets = Map.of();
        lines = List.of();
        noticeExpiresAt = 0;
        expiresAt = 0;
        dimension = null;
    }

    // ContainerTracker.item treats an empty tag like no tag, so normalize the same way here.
    private static CompoundTag tag(ItemStack stack) { return stack.hasTag() ? stack.getTag() : null; }

    private SearchController() {}
}
