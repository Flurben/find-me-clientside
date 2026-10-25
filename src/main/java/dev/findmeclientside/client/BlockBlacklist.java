package dev.findmeclientside.client;

import dev.findmeclientside.FindMeClientside;
import dev.findmeclientside.cache.BlacklistData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraftforge.common.Tags;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Blocks the block search skips. Defaults cover natural terrain and are regenerated only when the mod list changes. */
public final class BlockBlacklist {
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("findmeclientside-block-blacklist.json");
    // Natural generation, vanilla and modded: what carvers dig through, stone, soil, ores, plants, ice.
    private static final List<TagKey<Block>> TERRAIN = List.of(
            BlockTags.OVERWORLD_CARVER_REPLACEABLES, BlockTags.NETHER_CARVER_REPLACEABLES,
            BlockTags.BASE_STONE_OVERWORLD, BlockTags.BASE_STONE_NETHER, BlockTags.DIRT, BlockTags.SAND,
            BlockTags.TERRACOTTA, BlockTags.LEAVES, BlockTags.LOGS, BlockTags.FLOWERS, BlockTags.REPLACEABLE,
            BlockTags.ICE, BlockTags.SNOW, BlockTags.NYLIUM, BlockTags.WART_BLOCKS, BlockTags.CORAL_BLOCKS,
            BlockTags.CORALS, BlockTags.WALL_CORALS, BlockTags.MOSS_REPLACEABLE, BlockTags.LUSH_GROUND_REPLACEABLE,
            BlockTags.SCULK_REPLACEABLE, BlockTags.DRIPSTONE_REPLACEABLE, BlockTags.CAVE_VINES,
            Tags.Blocks.ORES, Tags.Blocks.GRAVEL, Tags.Blocks.SAND, Tags.Blocks.NETHERRACK, Tags.Blocks.END_STONES);
    private static BlacklistData data;
    private static Set<Block> resolved;

    public static boolean contains(Block block) {
        if (resolved == null) {
            Set<Block> blocks = new HashSet<>();
            for (String id : data().effective()) {
                ResourceLocation key = ResourceLocation.tryParse(id);
                if (key != null) BuiltInRegistries.BLOCK.getOptional(key).ifPresent(blocks::add);
            }
            resolved = blocks;
        }
        return resolved.contains(block);
    }

    static boolean contains(String id) { return data().contains(id); }
    static boolean isDefault(String id) { return data().isDefault(id); }
    static boolean isChanged(String id) { return data().isChanged(id); }
    static boolean generated() { return data().generated(); }

    static void set(String id, boolean blacklisted) {
        data().set(id, blacklisted);
        resolved = null;
    }

    static void resetToDefaults() {
        data().resetToDefaults();
        resolved = null;
    }

    /** Tags must be bound, so this runs when the client receives them on joining a world. */
    public static void regenerateIfModsChanged() {
        String modList = modList();
        if (modList.equals(data().modList())) return;
        List<String> terrain = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (isTerrain(block)) terrain.add(BuiltInRegistries.BLOCK.getKey(block).toString());
        }
        data().regenerate(modList, terrain);
        resolved = null;
        save();
        FindMeClientside.LOGGER.info("Generated the block search blacklist: {} terrain blocks", terrain.size());
    }

    static void save() {
        try {
            data().write(FILE);
        } catch (IOException failure) {
            FindMeClientside.LOGGER.warn("Could not save block search blacklist {}", FILE, failure);
        }
    }

    private static boolean isTerrain(Block block) {
        // Machines, storage and raw ore blocks are what players look for, even when a tag lists them.
        if (block instanceof EntityBlock || block.defaultBlockState().is(Tags.Blocks.STORAGE_BLOCKS)) return false;
        // Placed fluids: water and lava (including modded waters such as TerraFirmaCraft's) are terrain;
        // other fluids, such as oil, stay searchable.
        if (block instanceof LiquidBlock liquid) {
            var fluid = liquid.getFluid().defaultFluidState();
            return fluid.is(FluidTags.WATER) || fluid.is(FluidTags.LAVA);
        }
        // Modded plants are often untagged, so recognize them by class. Crops and saplings are player-made.
        if (block instanceof BushBlock) return !(block instanceof CropBlock || block instanceof SaplingBlock || block instanceof StemBlock);
        if (block instanceof LeavesBlock || block instanceof GrowingPlantBlock || block instanceof VineBlock
                || block instanceof SnowLayerBlock) return true;
        for (TagKey<Block> tag : TERRAIN) if (block.defaultBlockState().is(tag)) return true;
        return false;
    }

    // Mod IDs and versions; any install, removal or update triggers regeneration.
    private static String modList() {
        List<String> mods = new ArrayList<>();
        ModList.get().forEachModContainer((id, container) -> mods.add(id + "@" + container.getModInfo().getVersion()));
        mods.sort(null);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(String.join("\n", mods).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static BlacklistData data() {
        if (data == null) {
            try {
                data = BlacklistData.read(FILE);
            } catch (IOException failure) {
                FindMeClientside.LOGGER.warn("Could not read block search blacklist {}; regenerating it", FILE, failure);
                try {
                    Files.move(FILE, FILE.resolveSibling(FILE.getFileName() + ".unreadable-" + System.currentTimeMillis()));
                } catch (IOException ignored) {
                    // The next save replaces it.
                }
                data = new BlacklistData();
            }
        }
        return data;
    }

    private BlockBlacklist() {}
}
