package dev.findmeclientside.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.findmeclientside.FindMeClientside;
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
import net.minecraftforge.common.MinecraftForge;
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
    private static final KeyMapping CLEAR = new KeyMapping("key.findmeclientside.clear", KeyConflictContext.UNIVERSAL,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.findmeclientside");
    private static boolean emi;

    public static void register() {
        emi = ModList.get().isLoaded("emi");
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new ConfigScreen(parent)));
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ClientEvents::keys);
        MinecraftForge.EVENT_BUS.register(ClientEvents.class);
        FindMeClientside.LOGGER.info("Find Me Clientside initialized; EMI integration: {}", emi);
    }

    private static void keys(RegisterKeyMappingsEvent event) {
        event.register(SEARCH);
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
        while (SEARCH.consumeClick()) {
            if (mc.screen == null && mc.player != null && mc.level != null) {
                ItemStack held = mc.player.getMainHandItem();
                if (held.isEmpty()) held = mc.player.getOffhandItem();
                if (!held.isEmpty()) SearchController.search(List.of(held));
                else SearchController.notice(Component.translatable("findmeclientside.select_item"));
            }
        }
        while (CLEAR.consumeClick()) if (mc.screen == null) SearchController.clear();
    }

    @SubscribeEvent
    public static void key(ScreenEvent.KeyPressed.Pre event) {
        if (typing(event.getScreen())) return;
        if (CLEAR.isActiveAndMatches(InputConstants.getKey(event.getKeyCode(), event.getScanCode()))) {
            SearchController.clear();
            event.setCanceled(true);
        } else if (SEARCH.isActiveAndMatches(InputConstants.getKey(event.getKeyCode(), event.getScanCode()))
                && searchHovered()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void mouse(ScreenEvent.MouseButtonPressed.Pre event) {
        if (typing(event.getScreen())) return;
        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(event.getButton());
        if (CLEAR.isActiveAndMatches(key)) {
            SearchController.clear();
            event.setCanceled(true);
        } else if (SEARCH.isActiveAndMatches(key) && searchHovered()) event.setCanceled(true);
    }

    private static boolean typing(Screen screen) {
        return screen.getFocused() instanceof EditBox box && box.isFocused()
                || emi && EmiCompat.isTyping();
    }

    private static boolean searchHovered() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen == null) return false;
        int x = (int) (mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth());
        int y = (int) (mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight());
        if (emi) {
            List<ItemStack> stacks = EmiCompat.hovered(x, y);
            if (!stacks.isEmpty()) return SearchController.search(stacks);
        }
        if (mc.screen instanceof AbstractContainerScreen<?> screen) {
            var slot = screen.getSlotUnderMouse();
            if (slot != null && slot.hasItem()) return SearchController.search(List.of(slot.getItem().copy()));
        }
        return false;
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
                            ContainerTracker.INSTANCE.size(), emi ? "EMI" : "inventory",
                            dev.findmeclientside.ClientConfig.SEARCH_RADIUS.get()), false);
                    return 1;
                }))
                .then(Commands.literal("clear").executes(context -> {
                    ContainerTracker.INSTANCE.clear();
                    context.getSource().sendSuccess(() -> Component.translatable("findmeclientside.cleared"), false);
                    return 1;
                })));
    }

    private ClientEvents() {}
}
