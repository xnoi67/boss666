package com.bossghost.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.ModListScreen;

/** หน้าเมนูหลัก: MINEKARGONG พื้นดำ ปุ่มขาว ตัวหนังสือดำ */
public class BossTitleScreen extends Screen {
    public BossTitleScreen() {
        super(Component.literal("MINEKARGONG"));
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int y = this.height / 2 - 10;
        this.addRenderableWidget(new WhiteButton(x, y, 200, 20, Component.translatable("menu.singleplayer"),
                b -> this.minecraft.setScreen(new SelectWorldScreen(this))));
        y += 24;
        this.addRenderableWidget(new WhiteButton(x, y, 200, 20, Component.translatable("menu.multiplayer"),
                b -> this.minecraft.setScreen(new JoinMultiplayerScreen(this))));
        y += 24;
        this.addRenderableWidget(new WhiteButton(x, y, 200, 20, Component.translatable("fml.menu.mods"),
                b -> this.minecraft.setScreen(new ModListScreen(this))));
        y += 24;
        this.addRenderableWidget(new WhiteButton(x, y, 98, 20, Component.translatable("menu.options"),
                b -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options))));
        this.addRenderableWidget(new WhiteButton(x + 102, y, 98, 20, Component.translatable("menu.quit"),
                b -> this.minecraft.stop()));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xFF000000);

        Component title = Component.literal("MINEKARGONG").withStyle(ChatFormatting.BOLD);
        int tw = this.font.width(title);
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(this.width / 2.0F, this.height / 2.0F - 90.0F, 0.0F);
        ps.scale(4.0F, 4.0F, 1.0F);
        g.drawString(this.font, title, -tw / 2 + 1, 1, 0x8B0000, false);
        g.drawString(this.font, title, -tw / 2, 0, 0xFFFFFF, false);
        ps.popPose();

        g.drawString(this.font, "Created by ZIWX1", 4, this.height - 12, 0x888888, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
