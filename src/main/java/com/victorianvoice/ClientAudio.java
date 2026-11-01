package com.victorianvoice;

import com.mojang.logging.LogUtils;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

/** Plays received speech through the game's OpenAL context. Only ever loaded on the client. */
final class ClientAudio {
    private static final Logger LOG = LogUtils.getLogger();
    private static final ExecutorService AUDIO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "VictorianVoice-Audio");
        t.setDaemon(true);
        return t;
    });

    private ClientAudio() {}

    /** Called on the main thread for every received line. */
    static void onLine(VoiceLinePayload payload) {
        if (payload.pcm().length < 2) {
            ClientBubbles.show(payload);
            return;
        }
        AUDIO.execute(() -> {
            try {
                playBlocking(payload);
            } catch (Throwable t) {
                LOG.warn("Could not play a voice line", t);
            }
        });
    }

    private static void playBlocking(VoiceLinePayload p) throws InterruptedException {
        if (ALC10.alcGetCurrentContext() == 0L) {
            ClientBubbles.show(p);
            return;
        }
        int buffer = AL10.alGenBuffers();
        int source = AL10.alGenSources();
        ByteBuffer data = MemoryUtil.memAlloc(p.pcm().length);
        try {
            data.put(p.pcm());
            data.flip();
            AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, data, p.sampleRate());
        } finally {
            MemoryUtil.memFree(data);
        }
        AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0.0F);
        AL10.alSource3f(source, AL10.AL_POSITION, (float) p.x(), (float) p.y(), (float) p.z());
        AL10.alSourcef(source, AL10.AL_GAIN, gain(p));
        AL10.alSourcePlay(source);
        // Start the bubble at the same moment the audio starts so the typing matches the voice.
        ClientBubbles.show(p);

        long seconds = p.pcm().length / 2L / Math.max(1, p.sampleRate());
        long deadline = System.currentTimeMillis() + (seconds + 3L) * 1000L;
        while (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING
                && System.currentTimeMillis() < deadline) {
            AL10.alSourcef(source, AL10.AL_GAIN, gain(p));
            Thread.sleep(40L);
        }
        AL10.alSourceStop(source);
        AL10.alDeleteSources(source);
        AL10.alDeleteBuffers(buffer);
    }

    /** Linear falloff with distance, scaled by the player's Voice volume slider. */
    private static float gain(VoiceLinePayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0.0F;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double dist = cam.distanceTo(new Vec3(p.x(), p.y(), p.z()));
        double falloff = Math.max(0.0D, 1.0D - dist / Math.max(1, p.range()));
        float voice = mc.options.getSoundSourceVolume(SoundSource.VOICE);
        return (float) (p.gain() * falloff * voice);
    }
}
