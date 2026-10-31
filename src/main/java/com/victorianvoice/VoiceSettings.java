package com.victorianvoice;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.neoforged.fml.loading.FMLPaths;

public final class VoiceSettings {
    private static Boolean enabled;
    private VoiceSettings() {}

    public static boolean isTtsEnabled() {
        if (enabled != null) return enabled;
        Path file = FMLPaths.CONFIGDIR.get().resolve("victorian_voice.properties");
        Properties p = new Properties();
        try {
            if (Files.exists(file)) {
                try (var in = Files.newInputStream(file)) { p.load(in); }
            } else {
                p.setProperty("tts.enabled", "false");
                p.setProperty("tts.windowsVoice", "Microsoft David Desktop");
                try (var out = Files.newOutputStream(file)) { p.store(out, "Victorian Voice settings"); }
            }
        } catch (IOException ignored) {}
        enabled = Boolean.parseBoolean(p.getProperty("tts.enabled", "false"));
        return enabled;
    }
}
