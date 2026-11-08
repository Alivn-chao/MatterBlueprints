package com.gtnhblueprints.wireless;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widgets.ButtonWidget;

/** Keeps normal MUI click handling and sounds, and adds a local depressed face while held. */
final class WirelessMEButton extends ButtonWidget<WirelessMEButton> {
    private IDrawable depressed;
    private boolean pressed;

    WirelessMEButton(int top, int bottom, int hoverTop, int hoverBottom) {
        background(new WirelessMEFrame(top, bottom, false));
        hoverBackground(new WirelessMEFrame(hoverTop, hoverBottom, false));
        depressed = new WirelessMEFrame(bottom, top, true);
        disableThemeBackground(true);
        disableHoverThemeBackground(true);
    }

    static WirelessMEButton neutral() { return new WirelessMEButton(0xff666e72, 0xff41474b, 0xff858f94, 0xff535f65); }
    static WirelessMEButton cyan() { return new WirelessMEButton(0xff12a9b5, 0xff056571, 0xff31ced5, 0xff087f8c); }
    static WirelessMEButton red() { return new WirelessMEButton(0xffb62f49, 0xff68172d, 0xffdf4962, 0xff92253e); }
    static WirelessMEButton channel(boolean selected) {
        return selected ? new WirelessMEButton(0xff187e48, 0xff064e2c, 0xff279d60, 0xff0c7041)
            : new WirelessMEButton(0xff30373a, 0xff15191b, 0xff49585d, 0xff222c30);
    }

    @Override public Result onMousePressed(int mouseButton) {
        if (mouseButton == 0) pressed = true;
        return super.onMousePressed(mouseButton);
    }

    @Override public boolean onMouseRelease(int mouseButton) {
        pressed = false;
        return super.onMouseRelease(mouseButton);
    }

    @Override public void onMouseEndHover() { pressed = false; }

    @Override public IDrawable getCurrentBackground(WidgetThemeEntry<?> theme) {
        return pressed ? depressed : super.getCurrentBackground(theme);
    }

    @Override public IDrawable getCurrentOverlay(WidgetThemeEntry<?> theme) {
        IDrawable overlay = super.getCurrentOverlay(theme);
        if (!pressed || overlay == null) return overlay;
        return (context, x, y, width, height, widgetTheme) -> overlay.draw(context, x + 1, y + 1, width, height, widgetTheme);
    }
}
