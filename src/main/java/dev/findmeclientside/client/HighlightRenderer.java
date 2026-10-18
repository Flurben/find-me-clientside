package dev.findmeclientside.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ContainerScreenEvent;
import java.util.OptionalDouble;

public final class HighlightRenderer {
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(4096));

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
        for (var container : matches.stream().limit(256).toList()) {
            for (var anchor : container.anchors()) {
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
        Minecraft mc = Minecraft.getInstance();
        if (!SearchController.visible() || mc.options.hideGui) return;
        int available = Math.min(700, mc.getWindow().getGuiScaledWidth() - 24);
        if (available < 40) return;
        String title = mc.font.plainSubstrByWidth(SearchController.message().getString(), available);
        String detail = mc.font.plainSubstrByWidth(SearchController.detail().getString(), available);
        int width = Math.max(mc.font.width(title), mc.font.width(detail));
        int left = (mc.getWindow().getGuiScaledWidth() - width) / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.fill(left - 7, 7, left + width + 7, 37, 0xCC101820);
        graphics.fill(left - 7, 7, left - 5, 37, 0xFF42DBEF);
        graphics.drawString(mc.font, title, left, 12, 0xFF67E8F9, false);
        graphics.drawString(mc.font, detail, left, 24, 0xFFE2E8F0, false);
        graphics.pose().popPose();
    }

    private HighlightRenderer() {}
}
