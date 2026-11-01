package com.victorianvoice;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * One spoken line, sent from the server to nearby players. The text drives the speech bubble;
 * the PCM (16-bit mono little-endian, may be empty) drives the audio. revealMs is how long the
 * typewriter takes, so it can be matched to the audio length.
 */
public record VoiceLinePayload(int speakerId, String text, double x, double y, double z, int sampleRate,
                               int range, float gain, int revealMs, int holdMs, byte[] pcm)
        implements CustomPacketPayload {

    public static final Type<VoiceLinePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(VictorianVoice.MOD_ID, "voice_line"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VoiceLinePayload> CODEC =
        StreamCodec.of(VoiceLinePayload::write, VoiceLinePayload::read);

    private static void write(RegistryFriendlyByteBuf buf, VoiceLinePayload p) {
        buf.writeVarInt(p.speakerId);
        buf.writeUtf(p.text, 512);
        buf.writeDouble(p.x);
        buf.writeDouble(p.y);
        buf.writeDouble(p.z);
        buf.writeVarInt(p.sampleRate);
        buf.writeVarInt(p.range);
        buf.writeFloat(p.gain);
        buf.writeVarInt(p.revealMs);
        buf.writeVarInt(p.holdMs);
        buf.writeByteArray(p.pcm);
    }

    private static VoiceLinePayload read(RegistryFriendlyByteBuf buf) {
        int speakerId = buf.readVarInt();
        String text = buf.readUtf(512);
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        int rate = buf.readVarInt();
        int range = buf.readVarInt();
        float gain = buf.readFloat();
        int revealMs = buf.readVarInt();
        int holdMs = buf.readVarInt();
        byte[] pcm = buf.readByteArray();
        return new VoiceLinePayload(speakerId, text, x, y, z, rate, range, gain, revealMs, holdMs, pcm);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
