package dev.holdmytorch;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Brief client HUD feedback; one notice per blocked physical gesture. */
public final class GuardNotice {
    private static String action;
    private static Component title, hint;
    private static long until;

    static void show(String name, ClientConfig.Rule rule, String reason) {
        action = name;
        title = Component.translatable("holdmytorch.notice.blocked", Component.translatable(name));
        hint = reason != null ? Component.translatable(reason) : switch (rule.mode) {
            case HOLD -> Component.translatable("holdmytorch.notice.hold", rule.holdMs / 1000.0);
            case DOUBLE_TAP -> Component.translatable("holdmytorch.notice.double", rule.doubleMs);
            case CONFIRM_KEY -> Component.translatable("holdmytorch.notice.confirm", InputConstants.getKey(rule.confirmKey).getDisplayName());
        };
        until = System.nanoTime() / 1_000_000 + 2000;
    }

    static void clear() { action = null; title = null; hint = null; until = 0; }
    static void clear(String name) { if (name.equals(action)) clear(); }

    static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (title == null || System.nanoTime() / 1_000_000 >= until || !InputFilter.playing() || mc.options.hideGui) return;
        int width = mc.getWindow().getGuiScaledWidth();
        int maxWidth = Math.max(1, Math.min(200, width - 24));
        var lines = new ArrayList<>(mc.font.split(title, maxWidth));
        int titleLines = lines.size();
        lines.addAll(mc.font.split(hint, maxWidth));
        int textWidth = lines.stream().mapToInt(mc.font::width).max().orElse(0);
        int x = width - textWidth - 10;
        int bottom = mc.getWindow().getGuiScaledHeight() - (mc.options.showSubtitles().get() ? 90 : 60);
        int y = bottom - lines.size() * mc.font.lineHeight;
        graphics.fill(x - 4, y - 3, width - 6, bottom + 3, 0xB0000000);
        for (int i = 0; i < lines.size(); i++)
            graphics.drawString(mc.font, lines.get(i), x, y + i * mc.font.lineHeight, i < titleLines ? 0xFFCC66 : 0xFFFFFF, true);
    }
}
