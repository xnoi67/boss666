package com.bossghost.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** ปุ่มสีขาว ตัวหนังสือสีดำ */
public class WhiteButton extends Button {
    public WhiteButton(int x, int y, int w, int h, Component msg, OnPress onPress) {
        super(x, y, w, h, msg, onPress, DEFAULT_NARRATION);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int bg = this.isHoveredOrFocused() ? 0xFFC8C8C8 : 0xFFFFFFFF;
        g.fill(getX(), getY(), getX() + width, getY() + height, 0xFF777777);
        g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, bg);
        Font f = Minecraft.getInstance().font;
        Component msg = getMessage();
        g.drawString(f, msg, getX() + (width - f.width(msg)) / 2, getY() + (height - 8) / 2, 0x000000, false);
    }
}
