package dev.findmeclientside.client;

import dev.findmeclientside.ClientConfig;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

/** Opened by the Config button in the Forge mods list and in mod list replacements that reuse it. */
public final class ConfigScreen extends Screen {
    private final Screen parent;
    private OptionsList list;

    public ConfigScreen(Screen parent) {
        super(Component.translatable("findmeclientside.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        list = new OptionsList(minecraft, width, height, 32, height - 32, 25);
        list.addSmall(new OptionInstance<?>[] {
                range("radius", ClientConfig.SEARCH_RADIUS),
                range("highlightSeconds", ClientConfig.HIGHLIGHT_SECONDS),
                toggle("matchNbt", ClientConfig.MATCH_NBT),
                toggle("closeScreenOnSuccess", ClientConfig.CLOSE_SCREEN),
                toggle("persist", ClientConfig.PERSIST_CACHE),
                range("maxContainers", ClientConfig.MAX_CONTAINERS),
                range("maxAgeDays", ClientConfig.MAX_AGE_DAYS)});
        addWidget(list);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(width / 2 - 100, height - 27, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        list.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    // Values are set as they change; write the file once when the screen goes away.
    @Override
    public void removed() { ClientConfig.SPEC.save(); }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    private static OptionInstance<Boolean> toggle(String key, ForgeConfigSpec.BooleanValue value) {
        return OptionInstance.createBoolean("findmeclientside.config." + key, tooltip(value), value.get(), value::set);
    }

    // Ranges and tooltips come from the spec so the screen cannot drift from ClientConfig.
    private static OptionInstance<Integer> range(String key, ForgeConfigSpec.IntValue value) {
        ForgeConfigSpec.Range<Integer> range = spec(value).getRange();
        return new OptionInstance<>("findmeclientside.config." + key, tooltip(value), Options::genericValueLabel,
                new OptionInstance.IntRange(range.getMin(), range.getMax()), value.get(), value::set);
    }

    private static <T> OptionInstance.TooltipSupplier<T> tooltip(ForgeConfigSpec.ConfigValue<?> value) {
        String comment = spec(value).getComment();
        return comment == null || comment.isBlank() ? OptionInstance.noTooltip()
                : OptionInstance.cachedConstantTooltip(Component.literal(comment));
    }

    private static ForgeConfigSpec.ValueSpec spec(ForgeConfigSpec.ConfigValue<?> value) {
        return ClientConfig.SPEC.getSpec().get(value.getPath());
    }
}
