package com.victorianvoice;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One spoken line, sent from the server to nearby players. PCM is 16-bit mono little-endian. */
public record VoiceLinePayload(double x, double y, double z, int sampleRate, int range, float gain, byte[] pcm)
        implements CustomPacketPayload {

    public static final Type<VoiceLinePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(VictorianVoice.MOD_ID, "voice_line"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VoiceLinePayload> CODEC =
        StreamCodec.of(VoiceLinePayload::write, VoiceLinePayload::read);

    private static void write(RegistryFriendlyByteBuf buf, VoiceLinePayload p) {
        buf.writeDouble(p.x);
        buf.writeDouble(p.y);
        buf.writeDouble(p.z);
        buf.writeVarInt(p.sampleRate);
        buf.writeVarInt(p.range);
        buf.writeFloat(p.gain);
        buf.writeByteArray(p.pcm);
    }

    private static VoiceLinePayload read(RegistryFriendlyByteBuf buf) {
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        int rate = buf.readVarInt();
        int range = buf.readVarInt();
        float gain = buf.readFloat();
        byte[] pcm = buf.readByteArray();
        return new VoiceLinePayload(x, y, z, rate, range, gain, pcm);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
