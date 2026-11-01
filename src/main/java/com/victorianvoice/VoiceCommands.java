package com.victorianvoice;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** /victorianvoice setup <openai key> | status | speech on | speech off. Works in singleplayer or for ops. */
final class VoiceCommands {
    private VoiceCommands() {}

    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("victorianvoice")
            .requires(src -> src.hasPermission(2) || src.getServer().isSingleplayer())
            .then(Commands.literal("setup")
                .then(Commands.argument("openaiKey", StringArgumentType.greedyString())
                    .executes(ctx -> setup(ctx.getSource(), StringArgumentType.getString(ctx, "openaiKey")))))
            .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
            .then(Commands.literal("speech")
                .then(Commands.literal("on").executes(ctx -> speech(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> speech(ctx.getSource(), false)))));
    }

    private static int setup(CommandSourceStack src, String rawKey) {
        String key = rawKey.strip();
        if (!key.startsWith("sk-") || key.length() < 20) {
            src.sendFailure(Component.literal("That does not look like an OpenAI key (it should start with sk-)."));
            return 0;
        }
        VoiceConfig.OPENAI_KEY.set(key);
        VoiceConfig.PROVIDER.set("openai");
        VoiceConfig.TTS_ENABLED.set(true);
        VoiceConfig.OPENAI_KEY.save();
        src.sendSuccess(() -> Component.literal(
            "Victorian Voice: key saved, OpenAI translation and speech are on. Say something in chat."), false);
        return 1;
    }

    private static int speech(CommandSourceStack src, boolean on) {
        VoiceConfig.TTS_ENABLED.set(on);
        VoiceConfig.TTS_ENABLED.save();
        src.sendSuccess(() -> Component.literal("Victorian Voice: speech " + (on ? "on" : "off") + "."), false);
        return 1;
    }

    private static int status(CommandSourceStack src) {
        boolean hasKey = !VoiceConfig.resolveKey(VoiceConfig.OPENAI_KEY.get(), "OPENAI_API_KEY").isEmpty()
            || !VoiceConfig.resolveKey(VoiceConfig.TTS_KEY.get(), "OPENAI_API_KEY").isEmpty();
        String text = "Victorian Voice: translation=" + VoiceConfig.PROVIDER.get()
            + ", speech=" + (VoiceConfig.TTS_ENABLED.get() ? "on" : "off")
            + ", bubble=" + (VoiceConfig.BUBBLE_ENABLED.get() ? "on" : "off")
            + ", key=" + (hasKey ? "set" : "missing");
        src.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
