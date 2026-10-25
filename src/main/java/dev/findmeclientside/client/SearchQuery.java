package dev.findmeclientside.client;

import dev.findmeclientside.cache.ItemSnapshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** What one search looks for. Built once per key press; every check is a map lookup, not a string comparison. */
public final class SearchQuery {
    // Registries are frozen after loading, so the item -> placed blocks index is built once and reused.
    private static Map<Item, List<Block>> blocksByItem;

    private final Map<Item, List<CompoundTag>> items = new HashMap<>();
    private final Map<Fluid, List<CompoundTag>> fluids = new HashMap<>();
    private final Set<Block> blocks = new HashSet<>();
    private final List<ItemSnapshot> snapshots;
    private final Component name;

    private SearchQuery(List<ItemStack> itemStacks, List<FluidStack> fluidStacks) {
        Map<String, ItemSnapshot> unique = new LinkedHashMap<>();
        for (ItemStack stack : itemStacks) {
            items.computeIfAbsent(stack.getItem(), item -> new ArrayList<>()).add(tag(stack));
            blocks.addAll(blocksFor(stack.getItem()));
            ItemSnapshot snapshot = ContainerTracker.item(stack);
            unique.put("i" + snapshot.itemId() + "\n" + snapshot.tag(), snapshot);
        }
        for (FluidStack stack : fluidStacks) {
            fluids.computeIfAbsent(stack.getFluid(), fluid -> new ArrayList<>()).add(stack.hasTag() ? stack.getTag() : null);
            // Placed fluids, such as an oil spring, are found as their fluid block.
            Block fluidBlock = stack.getFluid().defaultFluidState().createLegacyBlock().getBlock();
            if (fluidBlock != Blocks.AIR) blocks.add(fluidBlock);
            ItemSnapshot snapshot = ContainerTracker.fluid(stack);
            unique.put("f" + snapshot.itemId() + "\n" + snapshot.tag(), snapshot);
        }
        snapshots = List.copyOf(unique.values());
        int total = itemStacks.size() + fluidStacks.size();
        if (total > 1 && !(itemStacks.size() == 1 && fluidStacks.size() == 1)) {
            name = Component.translatable("findmeclientside.alternatives", total);
        } else {
            name = itemStacks.isEmpty() ? fluidStacks.get(0).getDisplayName() : itemStacks.get(0).getHoverName();
        }
    }

    /**
     * @param containedFluids also search for fluid held by the items (buckets, cells, jugs), so hovering
     *                        a filled container finds tanks of that fluid as well
     */
    public static SearchQuery of(List<ItemStack> itemStacks, List<FluidStack> fluidStacks, boolean containedFluids) {
        List<ItemStack> itemList = new ArrayList<>();
        List<FluidStack> fluidList = new ArrayList<>();
        for (ItemStack stack : itemStacks) {
            if (stack.isEmpty()) continue;
            itemList.add(stack.copy());
            if (containedFluids) containedFluid(stack).ifPresent(fluidList::add);
        }
        for (FluidStack stack : fluidStacks) if (!stack.isEmpty()) fluidList.add(stack.copy());
        return itemList.isEmpty() && fluidList.isEmpty() ? null : new SearchQuery(itemList, fluidList);
    }

    public boolean matches(ItemStack stack, boolean matchNbt) {
        if (stack.isEmpty()) return false;
        List<CompoundTag> tags = items.get(stack.getItem());
        return tags != null && (!matchNbt || tags.contains(tag(stack)));
    }

    public boolean matches(FluidStack stack, boolean matchNbt) {
        if (stack.isEmpty()) return false;
        List<CompoundTag> tags = fluids.get(stack.getFluid());
        return tags != null && (!matchNbt || tags.contains(stack.hasTag() ? stack.getTag() : null));
    }

    /** Matches by item or fluid identity plus the raw tag; used for AE2 keys without converting them. */
    public boolean matches(Object itemOrFluid, CompoundTag tag, boolean matchNbt) {
        List<CompoundTag> tags = itemOrFluid instanceof Item item ? items.get(item)
                : itemOrFluid instanceof Fluid fluid ? fluids.get(fluid) : null;
        return tags != null && (!matchNbt || tags.contains(tag == null || tag.isEmpty() ? null : tag));
    }

    public Set<Block> blocks() { return blocks; }
    public boolean hasItems() { return !items.isEmpty(); }
    public boolean hasFluids() { return !fluids.isEmpty(); }
    public List<ItemSnapshot> snapshots() { return snapshots; }
    public Component name() { return name; }

    /** Fluid held by one of these items (bucket, cell, jug). Other mods' item capabilities must never crash a tick or frame. */
    static Optional<FluidStack> containedFluid(ItemStack stack) {
        try {
            return FluidUtil.getFluidContained(stack).filter(fluid -> !fluid.isEmpty());
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
    }

    // ContainerTracker.item treats an empty tag like no tag, so normalize the same way here.
    private static CompoundTag tag(ItemStack stack) { return stack.hasTag() ? stack.getTag() : null; }

    // Block.asItem() also maps variants such as wall torches and wall signs back to their item.
    private static List<Block> blocksFor(Item item) {
        if (blocksByItem == null) {
            Map<Item, List<Block>> index = new HashMap<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                Item asItem = block.asItem();
                if (asItem != Items.AIR) index.computeIfAbsent(asItem, i -> new ArrayList<>()).add(block);
            }
            blocksByItem = index;
        }
        return blocksByItem.getOrDefault(item, List.of());
    }
}
