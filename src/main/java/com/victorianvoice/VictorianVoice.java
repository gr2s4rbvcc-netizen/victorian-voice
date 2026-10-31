package com.victorianvoice;

import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientChatEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import java.util.Locale;

@Mod(VictorianVoice.MOD_ID)
public final class VictorianVoice {
    public static final String MOD_ID = "victorian_voice";

    public VictorianVoice() {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onOutgoingChat(ClientChatEvent event) {
        String original = event.getMessage();
        if (original == null || original.isBlank() || original.startsWith("/")) return;
        event.setMessage(VictorianTranslator.translate(original));
    }

    @SubscribeEvent
    public void onIncomingChat(ClientChatReceivedEvent event) {
        // Optional TTS is deliberately opt-in. Windows SAPI is used so no API key is needed.
        if (!VoiceSettings.isTtsEnabled()) return;
        String plain = event.getMessage().getString();
        if (plain == null || plain.isBlank()) return;
        VoiceSpeech.speak(plain);
    }
}
