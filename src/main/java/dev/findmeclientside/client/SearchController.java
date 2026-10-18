package dev.findmeclientside.client;

import dev.findmeclientside.ClientConfig;
import dev.findmeclientside.cache.ItemSnapshot;
import dev.findmeclientside.cache.RememberedContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class SearchController {
    private static List<RememberedContainer> matches = List.of();
    private static List<ItemSnapshot> query = List.of();
    private static Component message = Component.empty();
    private static String dimension;
    private static long expiresAt;

    public static boolean search(List<ItemStack> targets) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || targets.isEmpty()) return false;
        matches = ContainerTracker.INSTANCE.search(targets);
        query = ContainerTracker.queries(targets);
        dimension = ContainerTracker.dimension();
        expiresAt = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(ClientConfig.HIGHLIGHT_SECONDS.get());
        Component name = targets.size() == 1 ? targets.get(0).getHoverName()
                : Component.translatable("findmeclientside.alternatives", targets.size());
        if (matches.isEmpty()) {
            message = Component.translatable("findmeclientside.no_matches", name, ClientConfig.SEARCH_RADIUS.get());
        } else {
            long count = matches.stream().filter(c -> !c.kind().equals("ender_chest"))
                    .mapToLong(c -> c.matchingCount(query, ClientConfig.MATCH_NBT.get())).sum();
            count += matches.stream().filter(c -> c.kind().equals("ender_chest")).findFirst()
                    .map(c -> c.matchingCount(query, ClientConfig.MATCH_NBT.get())).orElse(0L);
            message = Component.translatable("findmeclientside.found", name, matches.size(), count);
            if (ClientConfig.CLOSE_SCREEN.get() && mc.screen != null) {
                ContainerTracker.INSTANCE.snapshot();
                if (mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
                mc.setScreen(null);
            }
        }
        mc.player.displayClientMessage(message, true);
        return true;
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
    public static Component message() { return message; }

    public static boolean matchesItem(ItemStack stack) {
        if (!visible() || stack.isEmpty()) return false;
        ItemSnapshot candidate = ContainerTracker.item(stack);
        return query.stream().anyMatch(target -> candidate.matches(target, ClientConfig.MATCH_NBT.get()));
    }

    public static Component detail() {
        return Component.translatable(matches.isEmpty() ? "findmeclientside.hint" : "findmeclientside.slot_hint");
    }

    public static void clear() {
        matches = List.of();
        query = List.of();
        expiresAt = 0;
        dimension = null;
    }

    private SearchController() {}
}
