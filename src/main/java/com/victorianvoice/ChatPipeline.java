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

/** Translates a chat line off-thread, broadcasts it, then optionally speaks it to nearby players. */
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

            if (!useSpeech) return;
            try {
                byte[] pcm = OnlineServices.speak(line);
                if (pcm.length == 0 || pcm.length > MAX_PCM_BYTES) {
                    LOG.warn("Speech audio was {} bytes, skipping", pcm.length);
                    return;
                }
                server.execute(() -> sendAudio(server, id, pcm));
            } catch (Exception e) {
                LOG.warn("Speech request failed: {}", e.toString());
            }
        });
    }

    private static void sendAudio(MinecraftServer server, UUID speakerId, byte[] pcm) {
        ServerPlayer speaker = server.getPlayerList().getPlayer(speakerId);
        if (speaker == null) return;
        Vec3 pos = speaker.getEyePosition();
        int range = VoiceConfig.TTS_RANGE.get();
        VoiceLinePayload payload = new VoiceLinePayload(pos.x, pos.y, pos.z,
            VoiceConfig.TTS_SAMPLE_RATE.get(), range, VoiceConfig.TTS_VOLUME.get().floatValue(), pcm);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level() != speaker.level()) continue;
            if (p.distanceToSqr(pos) > (double) range * range) continue;
            if (!p.connection.hasChannel(VoiceLinePayload.TYPE)) continue;
            PacketDistributor.sendToPlayer(p, payload);
        }
    }
}
