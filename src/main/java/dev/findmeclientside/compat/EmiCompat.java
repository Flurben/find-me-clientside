package dev.findmeclientside.compat;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.findmeclientside.FindMeClientside;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Field;
import java.util.List;

/** Loaded only after ModList confirms EMI is installed. Uses EMI's public API, plus reflection for EMI's own screens. */
public final class EmiCompat {
    private static boolean warned;

    public static boolean isTyping() { return EmiApi.isSearchFocused(); }

    public static List<ItemStack> hovered(int x, int y) {
        EmiIngredient hovered = EmiApi.getHoveredStack(x, y, true).getStack();
        if (hovered.isEmpty()) hovered = emiScreenHovered(Minecraft.getInstance().screen, x, y);
        return hovered.getEmiStacks().stream()
                .map(EmiStack::getItemStack).filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
    }

    // EMI's hover API only covers stack providers, inventory slots and its sidebars. Slots drawn inside
    // its own recipe and recipe tree screens are invisible to it, so ask those screens directly.
    private static EmiIngredient emiScreenHovered(Screen screen, int x, int y) {
        if (screen == null) return EmiStack.EMPTY;
        Class<?> type = screen.getClass();
        try {
            Object result = null;
            if (type.getName().equals("dev.emi.emi.screen.RecipeScreen")) {
                result = type.getMethod("getHoveredStack").invoke(screen);
            } else if (type.getName().equals("dev.emi.emi.screen.BoMScreen")) {
                Object hover = type.getMethod("getHoveredStack", int.class, int.class).invoke(screen, x, y);
                if (hover != null) {
                    Field stack = hover.getClass().getField("stack");
                    stack.setAccessible(true);
                    result = stack.get(hover);
                }
            }
            if (result instanceof EmiIngredient ingredient) return ingredient;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            if (!warned) {
                warned = true;
                FindMeClientside.LOGGER.warn("Could not read the hovered stack from EMI screen {}", type.getName(), failure);
            }
        }
        return EmiStack.EMPTY;
    }

    private EmiCompat() {}
}
