package com.victorianvoice;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class VoiceSpeech {
    private static final ExecutorService QUEUE = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "VictorianVoice-TTS");
        t.setDaemon(true);
        return t;
    });
    private VoiceSpeech() {}

    public static void speak(String text) {
        if (text == null || text.isBlank()) return;
        QUEUE.submit(() -> {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (!os.contains("win")) return;
            String safe = text.replace("'", "''").replace("\r", " ").replace("\n", " ");
            // Windows built-in speech engine; no network or API key is required.
            String script = "Add-Type -AssemblyName System.Speech; " +
                "$s=New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                "$s.Rate=0; $s.Speak('" + safe + "');";
            try {
                new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)
                    .redirectErrorStream(true).start().waitFor();
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            }
        });
    }
}
