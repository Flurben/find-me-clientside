package dev.findmeclientside.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.findmeclientside.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ContainerScreenEvent;
import java.util.List;
import java.util.OptionalDouble;

public final class HighlightRenderer {
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(4096));
    private static final int MARGIN = 4, PADDING = 3, ACCENT = 2, LINE = 10;

    private abstract static class Types extends RenderType {
        private Types() { super("unused", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES,
                256, false, false, () -> {}, () -> {}); }

        // ParticleEngine leaves depth testing enabled. Vanilla NO_DEPTH_TEST is a no-op,
        // so explicitly disable it for the markers and restore the stage's state afterward.
        private static final DepthTestStateShard SEE_THROUGH = new DepthTestStateShard("findmeclientside_see_through", 519) {
            @Override public void setupRenderState() { RenderSystem.disableDepthTest(); }
            @Override public void clearRenderState() { RenderSystem.enableDepthTest(); }
        };

        private static final RenderType HIGHLIGHT = create("findmeclientside_highlight",
                DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 4096, false, false,
                CompositeState.builder().setShaderState(RENDERTYPE_LINES_SHADER)
                        .setLineState(new LineStateShard(OptionalDouble.of(2.5)))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(SEE_THROUGH)
                        .setOutputState(PARTICLES_TARGET)
                        .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).createCompositeState(false));
    }

    public static void render(RenderLevelStageEvent event) {
        // Forge 1.20.1 passes the projection stack to AFTER_LEVEL, not the world view stack.
        // AFTER_PARTICLES supplies the same camera rotation used for blocks and block entities.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        var matches = SearchController.matches();
        if (matches.isEmpty() || mc.options.hideGui) return;
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        pose.pushPose();
        VertexConsumer buffer = BUFFERS.getBuffer(Types.HIGHLIGHT);
        float pulse = 0.65f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 180.0);
        for (int i = 0; i < Math.min(matches.size(), 256); i++) {
            for (var anchor : matches.get(i).anchors()) {
                AABB box = new AABB(ContainerTracker.position(anchor)).inflate(0.015)
                        .move(-camera.x, -camera.y, -camera.z);
                LevelRenderer.renderLineBox(pose, buffer, box, 0.15f, 0.9f, 1.0f, pulse);
            }
        }
        BUFFERS.endBatch(Types.HIGHLIGHT);
        pose.popPose();
    }

    public static void slots(ContainerScreenEvent.Render.Background event) {
        if (!SearchController.visible()) return;
        var screen = event.getContainerScreen();
        GuiGraphics graphics = event.getGuiGraphics();
        // This event runs after the menu background and before item icons/counts.
        for (var slot : screen.getMenu().slots) {
            if (slot.isActive() && !(slot.container instanceof Inventory)
                    && SearchController.matchesItem(slot.getItem())) {
                int x = screen.getGuiLeft() + slot.x;
                int y = screen.getGuiTop() + slot.y;
                graphics.fill(x, y, x + 16, y + 16, 0x9942DBEF);
            }
        }
    }

    public static void hud(GuiGraphics graphics) {
        if (Minecraft.getInstance().options.hideGui) return;
        List<String> lines = SearchController.panelLines();
        if (!lines.isEmpty()) panel(graphics, lines);
    }

    /** Draws the search panel at the configured size and position; the config screen uses it as a preview. */
    public static void panel(GuiGraphics graphics, List<String> lines) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        float scale = ClientConfig.HUD_SCALE.get() / 100f;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int maxText = (int) ((screenWidth - 2 * MARGIN) / scale) - ACCENT - 2 * PADDING;
        if (maxText < 40) return;
        int textWidth = 0;
        for (String line : lines) textWidth = Math.max(textWidth, Math.min(maxText, font.width(line)));
        int width = ACCENT + 2 * PADDING + textWidth;
        // Glyphs are 8px tall inside a 9px line; drop the trailing blank row so padding looks even.
        int height = 2 * PADDING + lines.size() * LINE - 2;
        // 0% puts the panel against the left/top margin and 100% against the right/bottom one.
        int left = Math.round(MARGIN + (screenWidth - 2 * MARGIN - width * scale) * ClientConfig.HUD_X.get() / 100f);
        int top = Math.round(MARGIN + (screenHeight - 2 * MARGIN - height * scale) * ClientConfig.HUD_Y.get() / 100f);
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 400);
        graphics.pose().scale(scale, scale, 1);
        graphics.fill(0, 0, width, height, 0xCC101820);
        graphics.fill(0, 0, ACCENT, height, 0xFF42DBEF);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (font.width(line) > maxText) line = font.plainSubstrByWidth(line, maxText);
            graphics.drawString(font, line, ACCENT + PADDING, PADDING + i * LINE, i == 0 ? 0xFF67E8F9 : 0xFFB4C0CC, false);
        }
        graphics.pose().popPose();
    }

    private HighlightRenderer() {}
}
