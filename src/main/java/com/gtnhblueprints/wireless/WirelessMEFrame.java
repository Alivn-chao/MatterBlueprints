package com.gtnhblueprints.wireless;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;

/** Pixel-aligned metal bevels and a restrained vertical sheen, without stretching border pixels. */
final class WirelessMEFrame implements IDrawable {
    private final Rectangle rim = new Rectangle().color(0xff101213).canApplyTheme(false);
    private final Rectangle light;
    private final Rectangle shade;
    private final Rectangle fill;
    private final Rectangle shine;

    WirelessMEFrame(int top, int bottom, boolean recessed) {
        light = new Rectangle().color(recessed ? 0xff090b0c : 0xffd8e2e5).canApplyTheme(false);
        shade = new Rectangle().color(recessed ? 0xff9ba3a6 : 0xff30383c).canApplyTheme(false);
        fill = new Rectangle().verticalGradient(top, bottom).canApplyTheme(false);
        shine = new Rectangle().color(recessed ? 0xff22282b : 0xffb6c7cc).canApplyTheme(false);
    }

    @Override public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme theme) {
        if (width < 5 || height < 5) return;
        rim.draw(context, x, y, width, height, theme);
        light.draw(context, x + 1, y + 1, width - 2, 1, theme);
        light.draw(context, x + 1, y + 2, 1, height - 3, theme);
        shade.draw(context, x + 2, y + height - 2, width - 3, 1, theme);
        shade.draw(context, x + width - 2, y + 2, 1, height - 4, theme);
        fill.draw(context, x + 2, y + 2, width - 4, height - 4, theme);
        shine.draw(context, x + 3, y + 3, width - 6, 1, theme);
    }

    @Override public boolean canApplyTheme() { return false; }
}
