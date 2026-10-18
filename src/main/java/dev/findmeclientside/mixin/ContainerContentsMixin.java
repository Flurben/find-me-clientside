package dev.findmeclientside.mixin;

import dev.findmeclientside.client.ContainerTracker;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

/** Client-only observer. Runs after vanilla has applied a server inventory snapshot. */
@Mixin(AbstractContainerMenu.class)
public abstract class ContainerContentsMixin {
    @Inject(method = "initializeContents", at = @At("TAIL"))
    private void findmeclientside$receivedContents(int stateId, List<ItemStack> items,
                                                  ItemStack carried, CallbackInfo callback) {
        ContainerTracker.INSTANCE.receivedContents((AbstractContainerMenu) (Object) this);
    }
}
