package com.bossghost.client;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** พูดข้อความด้วย Text-to-Speech ของระบบปฏิบัติการ (เสียงต่ำ ช้า ให้ฟังหลอนๆ แต่ยังชัด) */
public class VoiceUtil {
    public static void speak(String text) {
        Thread t = new Thread(() -> {
            try {
                String os = System.getProperty("os.name", "").toLowerCase();
                if (os.contains("win")) {
                    String ssml = "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" xml:lang=\"en-US\">"
                            + "<prosody pitch=\"x-low\" rate=\"slow\" volume=\"x-loud\">" + text + "</prosody></speak>";
                    String script = "Add-Type -AssemblyName System.Speech; "
                            + "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
                            + "try { $s.SpeakSsml('" + ssml.replace("'", "''") + "') } "
                            + "catch { $s.Rate = -3; $s.Speak('" + text.replace("'", "''") + "') }";
                    String enc = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
                    new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                            "-EncodedCommand", enc).redirectErrorStream(true).start().waitFor();
                } else if (os.contains("mac")) {
                    new ProcessBuilder("say", "-r", "100", text).start().waitFor();
                } else {
                    try {
                        new ProcessBuilder("espeak-ng", "-s", "100", "-p", "5", text).start().waitFor();
                    } catch (Exception e) {
                        new ProcessBuilder("espeak", "-s", "100", "-p", "5", text).start().waitFor();
                    }
                }
            } catch (Exception ignored) {
                // ไม่มี TTS ในเครื่อง ก็ข้ามไป (ยังมีข้อความในแชท)
            }
        }, "bossghost-voice");
        t.setDaemon(true);
        t.start();
    }
}
