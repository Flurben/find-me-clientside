package dev.findmeclientside.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Searchable, filterable list of every block; clicking a row adds it to or removes it from the blacklist. */
final class BlacklistScreen extends Screen {
    private enum Filter {
        ALL, EXCLUDED, SEARCHED;
        Component caption() { return Component.translatable("findmeclientside.blacklist.filter." + name().toLowerCase(Locale.ROOT)); }
    }

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private RowList list;
    private EditBox search;
    private Filter filter = Filter.ALL;
    private int shown;

    BlacklistScreen(Screen parent) {
        super(Component.translatable("findmeclientside.blacklist.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (rows.isEmpty()) {
            // Built once per screen; filtering only selects among these rows.
            for (Block block : BuiltInRegistries.BLOCK) rows.add(new Row(block));
            rows.sort(Comparator.comparing((Row row) -> row.key.getNamespace()).thenComparing(row -> row.name));
        }
        String previous = search == null ? "" : search.getValue();
        search = new EditBox(font, width / 2 - 155, 18, 205, 20, Component.translatable("findmeclientside.blacklist.search"));
        search.setHint(Component.translatable("findmeclientside.blacklist.search"));
        search.setValue(previous);
        search.setResponder(text -> refresh());
        addRenderableWidget(search);
        addRenderableWidget(CycleButton.builder(Filter::caption).withValues(Filter.values()).withInitialValue(filter)
                .create(width / 2 + 55, 18, 100, 20, Component.translatable("findmeclientside.blacklist.show"), (button, value) -> {
                    filter = value;
                    refresh();
                }));
        list = new RowList();
        addWidget(list);
        addRenderableWidget(Button.builder(Component.translatable("findmeclientside.blacklist.reset"), button -> {
            BlockBlacklist.resetToDefaults();
            refresh();
        }).bounds(width / 2 - 155, height - 26, 150, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(width / 2 + 5, height - 26, 150, 20).build());
        setInitialFocus(search);
        refresh();
    }

    private void refresh() {
        List<String> mods = new ArrayList<>(), words = new ArrayList<>();
        for (String token : search.getValue().toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (token.startsWith("@") && token.length() > 1) mods.add(token.substring(1));
            else if (!token.isEmpty() && !token.equals("@")) words.add(token);
        }
        List<Row> matching = new ArrayList<>();
        for (Row row : rows) {
            boolean excluded = BlockBlacklist.contains(row.id);
            if (filter == Filter.EXCLUDED && !excluded || filter == Filter.SEARCHED && excluded) continue;
            if (!mods.stream().allMatch(row.key.getNamespace()::contains)) continue;
            if (!words.stream().allMatch(row.text::contains)) continue;
            matching.add(row);
        }
        shown = matching.size();
        list.replaceEntries(matching);
        list.setScrollAmount(list.getScrollAmount());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        list.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 6, 0xFFFFFF);
        Component status = BlockBlacklist.generated()
                ? Component.translatable("findmeclientside.blacklist.status", shown, rows.size())
                : Component.translatable("findmeclientside.blacklist.not_generated");
        graphics.drawCenteredString(font, status, width / 2, height - 38, 0xA0A0A0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void removed() { BlockBlacklist.save(); }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    private final class RowList extends ObjectSelectionList<Row> {
        RowList() { super(BlacklistScreen.this.minecraft, BlacklistScreen.this.width, BlacklistScreen.this.height, 42, BlacklistScreen.this.height - 42, 22); }

        @Override
        public void replaceEntries(java.util.Collection<Row> entries) { super.replaceEntries(entries); }

        @Override
        public int getRowWidth() { return Math.min(width - 40, 360); }

        @Override
        protected int getScrollbarPosition() { return width / 2 + getRowWidth() / 2 + 4; }
    }

    private final class Row extends ObjectSelectionList.Entry<Row> {
        final Block block;
        final ResourceLocation key;
        final String id, name, text;
        private ItemStack icon;

        Row(Block block) {
            this.block = block;
            this.key = BuiltInRegistries.BLOCK.getKey(block);
            this.id = key.toString();
            this.name = block.getName().getString();
            this.text = (name + " " + id).toLowerCase(Locale.ROOT);
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            if (icon == null) {
                // Fluids have no block item; show their bucket instead.
                icon = new ItemStack(block);
                if (icon.isEmpty() && block instanceof LiquidBlock liquid) icon = new ItemStack(liquid.getFluid().getBucket());
            }
            graphics.renderItem(icon, left + 2, top + 2);
            boolean excluded = BlockBlacklist.contains(id);
            Component state = Component.translatable(excluded ? "findmeclientside.blacklist.excluded" : "findmeclientside.blacklist.searched");
            int stateWidth = font.width(state);
            int textWidth = width - 26 - stateWidth - 8;
            graphics.drawString(font, font.plainSubstrByWidth(name, textWidth), left + 22, top + 2, 0xFFFFFF, false);
            graphics.drawString(font, font.plainSubstrByWidth(id, textWidth), left + 22, top + 11, 0x808080, false);
            graphics.drawString(font, state, left + width - stateWidth - 4, top + 2, excluded ? 0xFF7070 : 0x80E090, false);
            if (BlockBlacklist.isChanged(id)) {
                Component changed = Component.translatable("findmeclientside.blacklist.changed");
                graphics.drawString(font, changed, left + width - font.width(changed) - 4, top + 11, 0x808080, false);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) return false;
            BlockBlacklist.set(id, !BlockBlacklist.contains(id));
            // Under a status filter the row no longer belongs; elsewhere it just redraws.
            if (filter != Filter.ALL) refresh();
            return true;
        }

        @Override
        public Component getNarration() { return Component.literal(name); }
    }
}
