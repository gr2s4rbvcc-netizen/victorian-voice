package com.victorianvoice;

import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

/** Translates a chat line off-thread, broadcasts it, then sends speech and a bubble to nearby players. */
final class ChatPipeline {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int MAX_PCM_BYTES = 1_000_000;
    private static final Map<UUID, Long> LAST_ONLINE = new ConcurrentHashMap<>();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "VictorianVoice-Net");
        t.setDaemon(true);
        return t;
    });

    private ChatPipeline() {}

    static void handle(MinecraftServer server, ServerPlayer player, String raw) {
        UUID id = player.getUUID();
        Component name = player.getDisplayName();

        boolean wantsOnline = !VoiceConfig.PROVIDER.get().equals("local");
        boolean wantsSpeech = VoiceConfig.TTS_ENABLED.get();
        long now = System.currentTimeMillis();
        Long last = LAST_ONLINE.get(id);
        boolean throttled = last != null && now - last < VoiceConfig.COOLDOWN_MS.get();
        if (!throttled && (wantsOnline || wantsSpeech)) LAST_ONLINE.put(id, now);
        boolean useOnline = wantsOnline && !throttled;
        boolean useSpeech = wantsSpeech && !throttled;

        POOL.execute(() -> {
            String text;
            try {
                text = useOnline ? OnlineServices.translate(raw) : VictorianTranslator.translate(raw);
            } catch (Exception e) {
                LOG.warn("Online translation failed, using built-in rules: {}", e.toString());
                text = VictorianTranslator.translate(raw);
            }
            final String line = text;
            server.execute(() -> server.getPlayerList().broadcastSystemMessage(
                Component.translatable("chat.type.text", name, Component.literal(line)), false));

            byte[] pcm = new byte[0];
            if (useSpeech) {
                try {
                    byte[] got = OnlineServices.speak(line);
                    if (got.length >= 2 && got.length <= MAX_PCM_BYTES) pcm = got;
                    else LOG.warn("Speech audio was {} bytes, skipping audio", got.length);
                } catch (Exception e) {
                    LOG.warn("Speech request failed: {}", e.toString());
                }
            }
            final byte[] audio = pcm;
            if (audio.length > 0 || VoiceConfig.BUBBLE_ENABLED.get()) {
                server.execute(() -> sendLine(server, id, line, audio));
            }
        });
    }

    private static void sendLine(MinecraftServer server, UUID speakerId, String line, byte[] pcm) {
        ServerPlayer speaker = server.getPlayerList().getPlayer(speakerId);
        if (speaker == null) return;
        Vec3 pos = speaker.getEyePosition();
        boolean bubble = VoiceConfig.BUBBLE_ENABLED.get();
        int audioRange = VoiceConfig.TTS_RANGE.get();
        int bubbleRange = VoiceConfig.BUBBLE_RANGE.get();
        int rate = VoiceConfig.TTS_SAMPLE_RATE.get();
        float gain = VoiceConfig.TTS_VOLUME.get().floatValue();

        // With audio, the typewriter takes slightly less than the audio so the last word lands as it is spoken.
        long audioMs = pcm.length > 0 ? pcm.length * 1000L / (2L * Math.max(1, rate)) : 0L;
        long typeMs = pcm.length > 0
            ? (long) (audioMs * 0.9D)
            : line.length() * 1000L / Math.max(1, VoiceConfig.BUBBLE_CPS.get());
        int revealMs = (int) Math.max(400L, typeMs);
        int holdMs = VoiceConfig.BUBBLE_HOLD_MS.get();
        String bubbleText = bubble ? line : "";

        VoiceLinePayload withAudio = new VoiceLinePayload(speaker.getId(), bubbleText, pos.x, pos.y, pos.z,
            rate, audioRange, gain, revealMs, holdMs, pcm);
        VoiceLinePayload silent = new VoiceLinePayload(speaker.getId(), bubbleText, pos.x, pos.y, pos.z,
            rate, audioRange, gain, revealMs, holdMs, new byte[0]);

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level() != speaker.level()) continue;
            double d2 = p.distanceToSqr(pos);
            boolean hears = pcm.length > 0 && d2 <= (double) audioRange * audioRange;
            boolean sees = bubble && d2 <= (double) bubbleRange * bubbleRange;
            if (!hears && !sees) continue;
            if (!p.connection.hasChannel(VoiceLinePayload.TYPE)) continue;
            PacketDistributor.sendToPlayer(p, hears ? withAudio : silent);
        }
    }
}
