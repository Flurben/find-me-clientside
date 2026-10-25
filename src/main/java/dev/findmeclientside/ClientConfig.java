package dev.findmeclientside;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue SEARCH_RADIUS;
    public static final ForgeConfigSpec.IntValue HIGHLIGHT_SECONDS;
    public static final ForgeConfigSpec.BooleanValue MATCH_NBT;
    public static final ForgeConfigSpec.BooleanValue PERSIST_CACHE;
    public static final ForgeConfigSpec.BooleanValue CLOSE_SCREEN;
    public static final ForgeConfigSpec.IntValue MAX_CONTAINERS;
    public static final ForgeConfigSpec.IntValue MAX_AGE_DAYS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Client only. Containers must be opened before their contents can be searched.").push("search");
        SEARCH_RADIUS = builder.comment("Maximum search distance in blocks, in your current dimension.")
                .defineInRange("radius", 64, 1, 512);
        HIGHLIGHT_SECONDS = builder.comment("How long search markers stay visible, in seconds.")
                .defineInRange("highlightSeconds", 12, 1, 120);
        MATCH_NBT = builder.comment("Require exact item NBT (damage, enchantments, names, etc.) when matching.")
                .define("matchNbt", false);
        CLOSE_SCREEN = builder.comment("Close the inventory/recipe screen after a successful search to see the markers.")
                .define("closeScreenOnSuccess", true);
        builder.pop().push("cache");
        PERSIST_CACHE = builder.comment("Remember contents between sessions in game-directory/findmeclientside.",
                        "Caches are separated by server/world and player UUID. Disabling this also ignores existing files.")
                .define("persist", true);
        MAX_CONTAINERS = builder.comment("Maximum containers remembered per server/world; the oldest are forgotten first.")
                .defineInRange("maxContainers", 4096, 64, 32768);
        MAX_AGE_DAYS = builder.comment("Discard containers not opened for this many days.")
                .defineInRange("maxAgeDays", 30, 1, 365);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientConfig() {}
}
