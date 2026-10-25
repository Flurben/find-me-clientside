package dev.findmeclientside.compat;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.client.gui.me.common.ClientReadOnlySlot;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.client.gui.me.common.RepoSlot;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.common.IClientRepo;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.slot.FakeSlot;
import dev.findmeclientside.cache.ItemSnapshot;
import dev.findmeclientside.client.SearchQuery;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Loaded only after ModList confirms AE2 is installed. Reads the terminal's client-side repository, which AE2
 * itself keeps current with incremental server updates while a terminal is open, so tracking a network costs
 * nothing per tick however large it is.
 */
public final class AeCompat {
    // An immutable view of one network entry; AE keys never change after creation.
    private record Entry(AEKey what, long amount) {}

    /** Terminal grid slots and filter ghosts show items that are not stored in the menu's block. */
    public static boolean isVirtual(Slot slot) {
        return slot instanceof ClientReadOnlySlot || slot instanceof FakeSlot;
    }

    /**
     * The Wireless Access Point a wireless terminal menu is linked to, read from the terminal item's NBT, which the
     * client has. Null for terminal blocks, unlinked terminals and other menus.
     */
    public static GlobalPos linkedAccessPoint(AbstractContainerMenu menu) {
        if (!(menu instanceof MEStorageMenu storage) || !(storage.getHost() instanceof ItemMenuHost host)) return null;
        try {
            ItemStack stack = host.getItemStack();
            if (stack.getItem() instanceof WirelessTerminalItem terminal) return terminal.getLinkedPosition(stack);
            // Add-on terminals that do not extend AE2's item may still store AE2's link tag.
            CompoundTag tag = stack.getTag();
            if (tag == null || !tag.contains("accessPoint", Tag.TAG_COMPOUND)) return null;
            return GlobalPos.CODEC.parse(NbtOps.INSTANCE, tag.get("accessPoint")).result().orElse(null);
        } catch (RuntimeException failure) {
            return null;
        }
    }

    public static boolean isTerminal(AbstractContainerMenu menu) {
        return menu instanceof MEStorageMenu storage && storage.getClientRepo() != null;
    }

    // Copies the network's stored entries; cheap even for large networks, as it only copies references.
    private static List<Entry> entries(AbstractContainerMenu menu) {
        if (!(menu instanceof MEStorageMenu storage)) return List.of();
        IClientRepo repo = storage.getClientRepo();
        if (repo == null) return List.of();
        List<Entry> entries = new ArrayList<>(repo.getAllEntries().size());
        for (GridInventoryEntry entry : repo.getAllEntries()) {
            if (entry.getWhat() != null && entry.getStoredAmount() > 0) entries.add(new Entry(entry.getWhat(), entry.getStoredAmount()));
        }
        return entries;
    }

    /**
     * Copies the open terminal's entries now and returns their conversion to cache data, to run on a worker
     * thread. Returns null when the menu is not a terminal.
     */
    public static Supplier<List<ItemSnapshot>> networkContents(AbstractContainerMenu menu) {
        if (!isTerminal(menu)) return null;
        List<Entry> entries = entries(menu);
        return () -> snapshots(entries);
    }

    // Registry lookups and NBT strings are the costly part for large networks.
    private static List<ItemSnapshot> snapshots(List<Entry> entries) {
        List<ItemSnapshot> snapshots = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            if (entry.what() instanceof AEItemKey item) {
                snapshots.add(new ItemSnapshot(BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),
                        item.hasTag() ? item.getTag().toString() : "", entry.amount(), false));
            } else if (entry.what() instanceof AEFluidKey fluid) {
                snapshots.add(new ItemSnapshot(BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString(),
                        fluid.hasTag() ? fluid.getTag().toString() : "", entry.amount(), true));
            }
        }
        return snapshots;
    }

    /**
     * Matching stored items and fluid (mB) in the open terminal's network, as {items, fluid}. One pass of
     * identity lookups over AE2's own repository, without converting entries; null if no terminal is open.
     */
    public static long[] liveTotals(AbstractContainerMenu menu, SearchQuery query, boolean matchNbt) {
        if (!(menu instanceof MEStorageMenu storage) || storage.getClientRepo() == null) return null;
        long[] totals = new long[2];
        for (GridInventoryEntry entry : storage.getClientRepo().getAllEntries()) {
            if (entry.getStoredAmount() <= 0) continue;
            if (entry.getWhat() instanceof AEItemKey item && query.matches(item.getItem(), item.getTag(), matchNbt)) {
                totals[0] += entry.getStoredAmount();
            } else if (entry.getWhat() instanceof AEFluidKey fluid && query.matches(fluid.getFluid(), fluid.getTag(), matchNbt)) {
                totals[1] += entry.getStoredAmount();
            }
        }
        return totals;
    }

    /**
     * Resolves the hovered terminal entry or wrapped stack. Terminals show fluids as wrapper items,
     * so searching the slot's item directly would look for the wrapper rather than the fluid.
     */
    public static SearchQuery hovered(Slot slot) {
        AEKey what = null;
        if (slot instanceof RepoSlot repoSlot && repoSlot.getEntry() != null) what = repoSlot.getEntry().getWhat();
        else if (slot.hasItem()) {
            GenericStack wrapped = GenericStack.unwrapItemStack(slot.getItem());
            if (wrapped != null) what = wrapped.what();
        }
        if (what instanceof AEItemKey item) return SearchQuery.of(List.of(item.toStack()), List.of(), true);
        if (what instanceof AEFluidKey fluid) return SearchQuery.of(List.of(), List.of(fluid.toStack(1000)), false);
        return null;
    }

    private AeCompat() {}
}
