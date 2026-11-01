package com.victorianvoice;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(VictorianVoice.MOD_ID)
public final class VictorianVoice {
    public static final String MOD_ID = "victorian_voice";

    public VictorianVoice(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, VoiceConfig.SPEC);
        modBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(this::onServerChat);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        // The lambda body only touches ClientAudio when it runs, so dedicated servers never load it.
        registrar.playToClient(VoiceLinePayload.TYPE, VoiceLinePayload.CODEC,
            (payload, context) -> ClientAudio.onLine(payload));
    }

    private void onServerChat(ServerChatEvent event) {
        if (!VoiceConfig.ENABLED.get()) return;
        String raw = event.getRawText();
        if (raw == null || raw.isBlank()) return;
        ServerPlayer player = event.getPlayer();
        event.setCanceled(true);
        ChatPipeline.handle(player.server, player, raw);
    }
}
