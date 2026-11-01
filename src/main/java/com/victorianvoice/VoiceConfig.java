package com.victorianvoice;

import java.util.Arrays;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side settings, written to config/victorian_voice-common.toml on first launch. */
public final class VoiceConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.ConfigValue<String> PROVIDER;
    public static final ModConfigSpec.ConfigValue<String> ANTHROPIC_KEY;
    public static final ModConfigSpec.ConfigValue<String> ANTHROPIC_MODEL;
    public static final ModConfigSpec.ConfigValue<String> OPENAI_KEY;
    public static final ModConfigSpec.ConfigValue<String> OPENAI_BASE;
    public static final ModConfigSpec.ConfigValue<String> OPENAI_MODEL;
    public static final ModConfigSpec.IntValue TIMEOUT_SECONDS;
    public static final ModConfigSpec.IntValue COOLDOWN_MS;

    public static final ModConfigSpec.BooleanValue TTS_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> TTS_BASE;
    public static final ModConfigSpec.ConfigValue<String> TTS_KEY;
    public static final ModConfigSpec.ConfigValue<String> TTS_MODEL;
    public static final ModConfigSpec.ConfigValue<String> TTS_VOICE;
    public static final ModConfigSpec.DoubleValue TTS_SPEED;
    public static final ModConfigSpec.IntValue TTS_RANGE;
    public static final ModConfigSpec.DoubleValue TTS_VOLUME;
    public static final ModConfigSpec.IntValue TTS_SAMPLE_RATE;

    public static final ModConfigSpec.BooleanValue BUBBLE_ENABLED;
    public static final ModConfigSpec.IntValue BUBBLE_CPS;
    public static final ModConfigSpec.IntValue BUBBLE_HOLD_MS;
    public static final ModConfigSpec.IntValue BUBBLE_RANGE;

    public static final ModConfigSpec SPEC;

    static {
        B.push("translation");
        ENABLED = B.comment("Rewrite player chat in Victorian English on the server.")
            .define("enabled", true);
        // Arrays.asList, not List.of: an immutable list throws when the config system tests a null value.
        PROVIDER = B.comment("local = built-in rules (free, no key). anthropic = Claude API. openai = OpenAI-compatible chat API.")
            .defineInList("provider", "local", Arrays.asList("local", "anthropic", "openai"));
        ANTHROPIC_KEY = B.comment("Anthropic API key. If blank, the ANTHROPIC_API_KEY environment variable is used.")
            .define("anthropicApiKey", "");
        ANTHROPIC_MODEL = B.comment("Model used when provider = anthropic.")
            .define("anthropicModel", "claude-haiku-5-5");
        OPENAI_KEY = B.comment("OpenAI-compatible API key. If blank, the OPENAI_API_KEY environment variable is used.")
            .define("openaiApiKey", "");
        OPENAI_BASE = B.comment("Base URL of an OpenAI-compatible API, without a trailing slash.")
            .define("openaiBaseUrl", "https://api.openai.com/v1");
        OPENAI_MODEL = B.comment("Chat model used when provider = openai.")
            .define("openaiModel", "gpt-4o-mini");
        TIMEOUT_SECONDS = B.comment("Give up on an online request after this many seconds and use the built-in rules instead.")
            .defineInRange("timeoutSeconds", 10, 2, 30);
        COOLDOWN_MS = B.comment("Minimum time between online requests per player. Faster messages use the free built-in rules and no speech. Protects your API bill.")
            .defineInRange("cooldownMillis", 2000, 0, 60000);
        B.pop();

        B.push("speech");
        TTS_ENABLED = B.comment("Speak translated chat aloud to nearby players who have the mod installed.")
            .define("enabled", false);
        TTS_BASE = B.comment("Base URL of an OpenAI-compatible text-to-speech API (POST /audio/speech).")
            .define("baseUrl", "https://api.openai.com/v1");
        TTS_KEY = B.comment("API key for speech. If blank, openaiApiKey and then the OPENAI_API_KEY environment variable are used.")
            .define("apiKey", "");
        TTS_MODEL = B.define("model", "tts-1");
        TTS_VOICE = B.comment("Voice name. fable has a British accent.")
            .define("voice", "fable");
        TTS_SPEED = B.defineInRange("speed", 1.0D, 0.5D, 2.0D);
        TTS_RANGE = B.comment("How many blocks away the speech can be heard.")
            .defineInRange("rangeBlocks", 32, 4, 128);
        TTS_VOLUME = B.defineInRange("volume", 1.0D, 0.0D, 2.0D);
        TTS_SAMPLE_RATE = B.comment("Sample rate of the raw PCM audio the speech API returns. OpenAI uses 24000.")
            .defineInRange("sampleRate", 24000, 8000, 48000);
        B.pop();

        B.push("bubble");
        BUBBLE_ENABLED = B.comment("Show an animated speech bubble above the speaker. Needs the mod on the viewer's side.")
            .define("enabled", true);
        BUBBLE_CPS = B.comment("Typing speed in characters per second when there is no speech audio. With speech, the typing matches the audio length instead.")
            .defineInRange("charsPerSecond", 22, 5, 80);
        BUBBLE_HOLD_MS = B.comment("How long the bubble stays after the last letter appears.")
            .defineInRange("holdMillis", 3500, 500, 20000);
        BUBBLE_RANGE = B.comment("How many blocks away the bubble can be seen.")
            .defineInRange("rangeBlocks", 48, 8, 128);
        B.pop();

        SPEC = B.build();
    }

    private VoiceConfig() {}

    static String resolveKey(String configured, String... envNames) {
        if (configured != null && !configured.isBlank()) return configured.strip();
        for (String env : envNames) {
            String v = System.getenv(env);
            if (v != null && !v.isBlank()) return v.strip();
        }
        return "";
    }
}
