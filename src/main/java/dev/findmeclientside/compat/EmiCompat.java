package dev.findmeclientside.compat;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Loaded only after ModList confirms EMI is installed. Uses only EMI's public API. */
public final class EmiCompat {
    public static boolean isTyping() { return EmiApi.isSearchFocused(); }

    public static List<ItemStack> hovered(int x, int y) {
        return EmiApi.getHoveredStack(x, y, true).getStack().getEmiStacks().stream()
                .map(EmiStack::getItemStack).filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
    }

    private EmiCompat() {}
}
