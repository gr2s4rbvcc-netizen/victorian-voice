package com.victorianvoice;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Matrix4f;

/**
 * Typewriter speech bubbles drawn above players' heads. The bubble is built from vanilla text
 * with a background colour (the same way text displays are drawn), so it needs no custom shaders.
 */
@EventBusSubscriber(modid = VictorianVoice.MOD_ID, value = Dist.CLIENT)
public final class ClientBubbles {
    private static final Map<Integer, Bubble> BUBBLES = new ConcurrentHashMap<>();
    private static final int MAX_LINE_WIDTH = 130;
    private static final long POP_MS = 220L;
    private static final long FADE_MS = 350L;
    private static final int TEXT_COLOR = 0xFF2B2118;
    private static final int BACKGROUND = 0xF0EFE6D2;

    private ClientBubbles() {}

    private static final class Bubble {
        final String text;
        final long start;
        final int revealMs;
        final int holdMs;
        List<int[]> spans;
        int width;

        Bubble(String text, long start, int revealMs, int holdMs) {
            this.text = text;
            this.start = start;
            this.revealMs = revealMs;
            this.holdMs = holdMs;
        }
    }

    static void show(VoiceLinePayload p) {
        if (p.text() == null || p.text().isBlank()) return;
        BUBBLES.put(p.speakerId(), new Bubble(p.text(), System.currentTimeMillis(), Math.max(300, p.revealMs()), p.holdMs()));
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        int id = event.getEntity().getId();
        Bubble b = BUBBLES.get(id);
        if (b == null) return;

        long elapsed = System.currentTimeMillis() - b.start;
        long total = (long) b.revealMs + b.holdMs;
        if (elapsed > total + FADE_MS) {
            BUBBLES.remove(id, b);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (b.spans == null) layout(b, font);

        float pop = easeOutBack(clamp01((float) elapsed / POP_MS));
        float out = elapsed > total ? 1.0F - clamp01((float) (elapsed - total) / FADE_MS) : 1.0F;
        float scale = 0.025F * pop * out;
        if (scale < 0.001F) return;

        double bob = Math.sin(elapsed / 350.0D) * 0.02D;
        int shown = (int) Math.floor(b.text.length() * Math.min(1.0D, (double) elapsed / b.revealMs));
        int target = b.width + 8;
        int lines = b.spans.size();

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        poseStack.pushPose();
        poseStack.translate(0.0D, event.getEntity().getBbHeight() + 0.9D + bob, 0.0D);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-scale, -scale, scale);
        Matrix4f matrix = poseStack.last().pose();
        float x = -target / 2.0F;

        for (int i = 0; i < lines; i++) {
            int[] span = b.spans.get(i);
            int visible = Math.max(0, Math.min(span[1] - span[0], shown - span[0]));
            StringBuilder line = new StringBuilder(" ").append(b.text, span[0], span[0] + visible);
            while (font.width(line.toString()) < target) line.append(' ');
            font.drawInBatch(line.toString(), x, -10.0F * (lines - i), TEXT_COLOR, false, matrix, buffers,
                Font.DisplayMode.POLYGON_OFFSET, BACKGROUND, LightTexture.FULL_BRIGHT);
        }
        // Stepped pointer under the bubble
        font.drawInBatch("   ", -6.0F, 0.0F, TEXT_COLOR, false, matrix, buffers,
            Font.DisplayMode.POLYGON_OFFSET, BACKGROUND, LightTexture.FULL_BRIGHT);
        font.drawInBatch(" ", -2.0F, 10.0F, TEXT_COLOR, false, matrix, buffers,
            Font.DisplayMode.POLYGON_OFFSET, BACKGROUND, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    /** Word-wraps the full text once, so the bubble keeps its size while the letters appear. */
    private static void layout(Bubble b, Font font) {
        String text = b.text;
        int n = text.length();
        List<int[]> spans = new ArrayList<>();
        int widest = 0;
        int i = 0;
        while (i < n) {
            int start = i;
            int lastSpace = -1;
            int j = i;
            while (j < n && font.width(text.substring(start, j + 1)) <= MAX_LINE_WIDTH) {
                if (text.charAt(j) == ' ') lastSpace = j;
                j++;
            }
            int end;
            if (j >= n) {
                end = n;
                i = n;
            } else if (lastSpace > start) {
                end = lastSpace;
                i = lastSpace + 1;
            } else {
                end = Math.max(j, start + 1);
                i = end;
            }
            spans.add(new int[] {start, end});
            widest = Math.max(widest, font.width(text.substring(start, end)));
        }
        if (spans.isEmpty()) spans.add(new int[] {0, 0});
        b.width = widest;
        b.spans = spans;
    }

    private static float clamp01(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float u = t - 1.0F;
        return 1.0F + c3 * u * u * u + c1 * u * u;
    }
}
