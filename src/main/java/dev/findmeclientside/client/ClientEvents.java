package dev.findmeclientside.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.findmeclientside.FindMeClientside;
import dev.findmeclientside.compat.AeCompat;
import dev.findmeclientside.compat.EmiCompat;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;
import java.util.List;

public final class ClientEvents {
    private static final KeyMapping SEARCH = new KeyMapping("key.findmeclientside.search", KeyConflictContext.UNIVERSAL,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, "key.categories.findmeclientside");
    // Shift+Y by default: Y finds where an item is stored, Shift+Y finds where it is placed.
    private static final KeyMapping BLOCKS = new KeyMapping("key.findmeclientside.blocks", KeyConflictContext.UNIVERSAL,
            KeyModifier.SHIFT, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, "key.categories.findmeclientside");
    private static final KeyMapping CLEAR = new KeyMapping("key.findmeclientside.clear", KeyConflictContext.UNIVERSAL,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.findmeclientside");
    private static boolean emi;
    private static boolean ae2;

    public static void register() {
        emi = ModList.get().isLoaded("emi");
        ae2 = ModList.get().isLoaded("ae2");
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new ConfigScreen(parent)));
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ClientEvents::keys);
        MinecraftForge.EVENT_BUS.register(ClientEvents.class);
        FindMeClientside.LOGGER.info("Find Me Clientside initialized; EMI integration: {}, AE2 integration: {}", emi, ae2);
    }

    /** Whether AE2 is installed, so its compatibility class may be loaded. */
    static boolean ae2() { return ae2; }

    private static void keys(RegisterKeyMappingsEvent event) {
        event.register(SEARCH);
        event.register(BLOCKS);
        event.register(CLEAR);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void interact(PlayerInteractEvent.RightClickBlock event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getLevel().isClientSide && event.getEntity() == mc.player
                && event.getUseBlock() != net.minecraftforge.eventbus.api.Event.Result.DENY) {
            ContainerTracker.INSTANCE.clicked(event.getPos());
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ContainerTracker.INSTANCE.tick();
        SearchController.tick();
        boolean blocks = false;
        while (BLOCKS.consumeClick()) {
            blocks = true;
            if (mc.screen == null) searchHeld(true);
        }
        while (SEARCH.consumeClick()) {
            // Forge also reports an unmodified binding while Shift is held; Shift+Y belongs to the block search.
            if (blocks && SEARCH.getKey().equals(BLOCKS.getKey())) continue;
            if (mc.screen == null) searchHeld(false);
        }
        while (CLEAR.consumeClick()) if (mc.screen == null) SearchController.clear();
    }

    private static void searchHeld(boolean blocks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ItemStack held = mc.player.getMainHandItem();
        if (held.isEmpty()) held = mc.player.getOffhandItem();
        if (!held.isEmpty()) SearchController.search(SearchQuery.of(List.of(held), List.of(), true), blocks);
        else SearchController.notice(Component.translatable("findmeclientside.select_item"));
    }

    @SubscribeEvent
    public static void key(ScreenEvent.KeyPressed.Pre event) {
        if (typing(event.getScreen())) return;
        if (pressed(InputConstants.getKey(event.getKeyCode(), event.getScanCode()))) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void mouse(ScreenEvent.MouseButtonPressed.Pre event) {
        if (typing(event.getScreen())) return;
        if (pressed(InputConstants.Type.MOUSE.getOrCreate(event.getButton()))) event.setCanceled(true);
    }

    // The block search is checked first: an unmodified Y also matches while Shift is held.
    private static boolean pressed(InputConstants.Key key) {
        if (CLEAR.isActiveAndMatches(key)) {
            SearchController.clear();
            return true;
        }
        if (BLOCKS.isActiveAndMatches(key)) return searchHovered(true);
        return SEARCH.isActiveAndMatches(key) && searchHovered(false);
    }

    private static boolean typing(Screen screen) {
        return screen.getFocused() instanceof EditBox box && box.isFocused()
                || emi && EmiCompat.isTyping();
    }

    private static boolean searchHovered(boolean blocks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen == null) return false;
        int x = (int) (mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth());
        int y = (int) (mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight());
        var slot = mc.screen instanceof AbstractContainerScreen<?> screen ? screen.getSlotUnderMouse() : null;
        // AE2 terminals show fluids as wrapper items; resolve them before EMI reports the wrapper.
        if (ae2 && slot != null) {
            SearchQuery query = AeCompat.hovered(slot);
            if (query != null) return SearchController.search(query, blocks);
        }
        if (emi) {
            SearchQuery query = EmiCompat.hovered(x, y);
            if (query != null) return SearchController.search(query, blocks);
        }
        if (slot != null && slot.hasItem()) return SearchController.search(SearchQuery.of(List.of(slot.getItem()), List.of(), true), blocks);
        return false;
    }

    // Tags are bound only once a world is joined; the blacklist's terrain defaults depend on them.
    @SubscribeEvent
    public static void tags(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED) BlockBlacklist.regenerateIfModsChanged();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { ContainerTracker.INSTANCE.disconnect(); }

    @SubscribeEvent
    public static void renderWorld(RenderLevelStageEvent event) { HighlightRenderer.render(event); }

    @SubscribeEvent
    public static void renderSlots(ContainerScreenEvent.Render.Background event) { HighlightRenderer.slots(event); }

    @SubscribeEvent
    public static void renderHud(RenderGuiEvent.Post event) {
        if (Minecraft.getInstance().screen == null) HighlightRenderer.hud(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void renderScreen(ScreenEvent.Render.Post event) { HighlightRenderer.hud(event.getGuiGraphics()); }

    @SubscribeEvent
    public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("findmeclient")
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.translatable("findmeclientside.command_help"), false);
                    return 1;
                })
                .then(Commands.literal("stats").executes(context -> {
                    context.getSource().sendSuccess(() -> Component.translatable("findmeclientside.stats",
                            ContainerTracker.INSTANCE.size(), integrations(),
                            dev.findmeclientside.ClientConfig.SEARCH_RADIUS.get()), false);
                    return 1;
                }))
                .then(Commands.literal("clear").executes(context -> {
                    ContainerTracker.INSTANCE.clear();
                    context.getSource().sendSuccess(() -> Component.translatable("findmeclientside.cleared"), false);
                    return 1;
                })));
    }

    private static String integrations() {
        List<String> names = new java.util.ArrayList<>(List.of("inventory"));
        if (emi) names.add("EMI");
        if (ae2) names.add("AE2");
        return String.join(", ", names);
    }

    private ClientEvents() {}
}
