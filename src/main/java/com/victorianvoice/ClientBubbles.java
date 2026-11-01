package com.victorianvoice;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
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
    private static final Random RANDOM = new Random();

    private ClientBubbles() {}

    private static final class Bubble {
        final String text;
        final long start;
        final int revealMs;
        final int holdMs;
        List<int[]> spans;
        int width;
        long nextGesture;
        boolean leftNext;

        Bubble(String text, long start, int revealMs, int holdMs) {
            this.text = text;
            this.start = start;
            this.revealMs = revealMs;
            this.holdMs = holdMs;
        }
    }

    static void show(VoiceLinePayload p) {
        if (p.text() == null || p.text().isBlank()) return;
        Bubble b = new Bubble(p.text(), System.currentTimeMillis(), Math.max(300, p.revealMs()), p.holdMs());
        b.nextGesture = b.start + 120L;
        BUBBLES.put(p.speakerId(), b);
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

    /** While a line is being spoken, the speaker gestures with alternating arms. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<Integer, Bubble> entry : BUBBLES.entrySet()) {
            Bubble b = entry.getValue();
            if (now - b.start > b.revealMs || now < b.nextGesture) continue;
            Entity speaker = mc.level.getEntity(entry.getKey());
            if (speaker instanceof LivingEntity living) {
                living.swing(b.leftNext ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
                b.leftNext = !b.leftNext;
            }
            b.nextGesture = now + 500L + RANDOM.nextInt(450);
        }
    }

    /** In first person your own body is not drawn, so your bubble is shown on screen instead. */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !mc.options.getCameraType().isFirstPerson()) return;
        int id = mc.player.getId();
        Bubble b = BUBBLES.get(id);
        if (b == null) return;
        long elapsed = System.currentTimeMillis() - b.start;
        if (elapsed > (long) b.revealMs + b.holdMs + FADE_MS) {
            BUBBLES.remove(id, b);
            return;
        }
        Font font = mc.font;
        if (b.spans == null) layout(b, font);

        int shown = (int) Math.floor(b.text.length() * Math.min(1.0D, (double) elapsed / b.revealMs));
        boolean typing = shown < b.text.length();
        int lines = b.spans.size();
        int w = b.width + 14;
        int h = lines * 10 + 8;
        GuiGraphics g = event.getGuiGraphics();
        int x = (g.guiWidth() - w) / 2;
        int y = 28;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF7A5C3A);
        g.fill(x, y, x + w, y + h, BACKGROUND);
        for (int i = 0; i < lines; i++) {
            int[] span = b.spans.get(i);
            int visible = Math.max(0, Math.min(span[1] - span[0], shown - span[0]));
            String line = b.text.substring(span[0], span[0] + visible);
            if (typing && shown >= span[0] && shown <= span[1] && (elapsed / 250L) % 2L == 0L) line += "_";
            g.drawString(font, line, x + 7, y + 5 + i * 10, TEXT_COLOR, false);
        }
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
