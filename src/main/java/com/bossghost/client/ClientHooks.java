package com.bossghost.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

public class ClientHooks {
    public static int jumpscareTicks = 0;

    public static void handle(int type) {
        Minecraft mc = Minecraft.getInstance();
        if (type == 0) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.SOUL_ESCAPE, 0.5F, 1.0F));
            VoiceUtil.speak("Welcome... to Boss... Kragong. ... Created by... Z. I. W. X. One.");
        } else if (type == 1) {
            jumpscareTicks = 40;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.GHAST_SCREAM, 0.6F, 1.0F));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ENDERMAN_SCREAM, 0.7F, 1.0F));
        }
    }
}
