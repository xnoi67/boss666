package com.bossghost.client;

import com.bossghost.BossGhostMod;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BossGhostMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientForgeEvents {
    private static final ResourceLocation JUMPSCARE =
            new ResourceLocation(BossGhostMod.MODID, "textures/gui/jumpscare.png");

    /** เปลี่ยนหน้า Title เป็นหน้าของมอด */
    @SubscribeEvent
    public static void onOpenScreen(ScreenEvent.Opening e) {
        if (e.getNewScreen() instanceof TitleScreen) {
            e.setNewScreen(new BossTitleScreen());
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.END && ClientHooks.jumpscareTicks > 0) {
            ClientHooks.jumpscareTicks--;
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post e) {
        int t = ClientHooks.jumpscareTicks;
        if (t <= 0) return;

        GuiGraphics g = e.getGuiGraphics();
        int w = g.guiWidth();
        int h = g.guiHeight();
        int shake = 8;
        int ox = (int) ((Math.random() - 0.5D) * shake * 2);
        int oy = (int) ((Math.random() - 0.5D) * shake * 2);

        RenderSystem.enableBlend();
        g.fill(0, 0, w, h, 0xFF000000);
        int size = Math.max(w, h);
        g.blit(JUMPSCARE, (w - size) / 2 + ox, (h - size) / 2 + oy, size, size, 0.0F, 0.0F, 64, 64, 64, 64);
        if (t % 4 < 2) g.fill(0, 0, w, h, 0x55FF0000);
    }
}
