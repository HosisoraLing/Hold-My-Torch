package dev.holdmytorch;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.ToggleKeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Native widgets shared by both versions. Changes save when leaving the root menu. */
public final class ConfigScreen extends Screen {
    private final Screen parent;
    private String section = "home";
    private String selectedKey;
    private String search = "";
    private int page;
    private ClientConfig.Rule capturing;
    private String error = "";
    private int left;
    private int row;

    public ConfigScreen(Screen parent) {
        super(Component.literal("Hold My Torch! — Client Config"));
        this.parent = parent;
    }

    private Component tr(String key) { return Component.translatable("holdmytorch." + key); }
    private void navigate(String target) { capturing = null; section = target; rebuildWidgets(); }

    @Override protected void init() {
        left = width / 2 - 150;
        row = 36;
        ClientConfig c = ClientConfig.current;
        if (section.equals("home")) {
            button(tr("torch"), b -> navigate("torch"));
            button(tr("drop"), b -> navigate("drop"));
            button(tr("custom"), b -> navigate("custom"));
            button(tr("global_reset"), b -> minecraft.setScreen(new ConfirmScreen(yes -> {
                if (yes) { ClientConfig.current = new ClientConfig(); InputFilter.reset(); }
                minecraft.setScreen(this);
            }, tr("global_reset"), tr("reset_question"))));
        } else if (section.equals("custom")) {
            bool("enabled", () -> c.customEnabled, () -> c.customEnabled = !c.customEnabled, true);
            button(tr("bindings"), b -> navigate("bindings"));
            button(tr("module_reset"), b -> {
                c.keys.clear(); c.customEnabled = true; rebuildWidgets();
            }).active = !c.keys.isEmpty() || !c.customEnabled;
        } else if (section.equals("bindings")) {
            EditBox box = new EditBox(font, left, row, 300, 20, tr("search"));
            box.setMaxLength(100); box.setValue(search);
            box.setResponder(value -> { search = value; page = 0; refreshBindings(); });
            addRenderableWidget(box); row += 26;
            int count = Math.max(1, (height - 140) / 24);
            List<KeyMapping> keys = bindings();
            page = Math.min(page, Math.max(0, (keys.size() - 1) / count));
            for (KeyMapping key : keys.stream().skip((long) page * count).limit(count).toList()) {
                String name = key.getName();
                Button entry = button(Component.literal((c.keys.containsKey(name) ? "* " : "")
                        + Component.translatable(name).getString()), b -> {
                    selectedKey = name; c.keys.computeIfAbsent(name, k -> new ClientConfig.Rule()); navigate("rule");
                });
                entry.setTooltip(Tooltip.create(Component.literal(name + " · " + key.getTranslatedKeyMessage().getString())));
                if (key instanceof ToggleKeyMapping) {
                    entry.active = false;
                    entry.setTooltip(Tooltip.create(tr("unsupported_toggle")));
                }
            }
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); })
                    .bounds(left, height - 62, 50, 20).build()).active = page > 0;
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); })
                    .bounds(left + 250, height - 62, 50, 20).build()).active = (page + 1) * count < keys.size();
        } else {
            ClientConfig.Rule r = section.equals("torch") ? c.torch : section.equals("drop") ? c.drop : c.keys.get(selectedKey);
            bool("enabled", () -> r.enabled, () -> r.enabled = !r.enabled, true);
            choice("mode", () -> tr("mode." + r.mode.name().toLowerCase(java.util.Locale.ROOT)),
                    () -> { r.mode = Confirmation.Mode.values()[(r.mode.ordinal() + 1) % 3]; rebuildWidgets(); },
                    () -> r.mode = Confirmation.Mode.HOLD, () -> r.mode != Confirmation.Mode.HOLD);
            if (r.mode == Confirmation.Mode.HOLD) number("hold", () -> r.holdMs, value -> r.holdMs = value, 500, 100, 10000);
            if (r.mode == Confirmation.Mode.DOUBLE_TAP) number("double", () -> r.doubleMs, value -> r.doubleMs = value, 300, 100, 2000);
            if (r.mode == Confirmation.Mode.CONFIRM_KEY) choice("confirm", () -> capturing == r ? tr("capture") : InputConstants.getKey(r.confirmKey).getDisplayName(),
                    () -> { capturing = r; rebuildWidgets(); }, () -> { r.confirmKey = "key.keyboard.left.alt"; capturing = null; },
                    () -> !r.confirmKey.equals("key.keyboard.left.alt"));
            if (section.equals("torch")) {
                bool("main_hand", () -> c.protectMainHand, () -> c.protectMainHand = !c.protectMainHand, false);
                number("brightness", () -> c.brightnessThreshold, value -> c.brightnessThreshold = value, 12, 0, 15);
                bool("dynamic", () -> c.dynamicLights, () -> c.dynamicLights = !c.dynamicLights, true);
            }
            button(tr("module_reset"), b -> {
                if (section.equals("torch")) { c.torch = new ClientConfig.Rule(); c.protectMainHand = false; c.brightnessThreshold = 12; c.dynamicLights = true; }
                else if (section.equals("drop")) c.drop = new ClientConfig.Rule();
                else c.keys.put(selectedKey, new ClientConfig.Rule());
                capturing = null; rebuildWidgets();
            });
            if (section.equals("rule")) button(tr("remove_rule"), b -> { c.keys.remove(selectedKey); navigate("bindings"); });
        }
        addRenderableWidget(Button.builder(tr(section.equals("home") ? "save_close" : "back"), b -> onClose())
                .bounds(left + 75, height - 28, 150, 20).build());
    }

    private void refreshBindings() {
        // Preserve the search box focus while rebuilding just the displayed results next frame.
        minecraft.execute(() -> { if (minecraft.screen == this) { rebuildWidgets();
            for (var child : children()) if (child instanceof EditBox box) { setFocused(box); box.setCursorPosition(box.getValue().length()); break; }
        }});
    }

    private List<KeyMapping> bindings() {
        String query = search.toLowerCase(java.util.Locale.ROOT);
        return Arrays.stream(minecraft.options.keyMappings).filter(k -> k.getName().toLowerCase(java.util.Locale.ROOT).contains(query)
                || Component.translatable(k.getName()).getString().toLowerCase(java.util.Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(k -> Component.translatable(k.getName()).getString())).toList();
    }

    private Button button(Component text, Button.OnPress action) {
        Button result = addRenderableWidget(Button.builder(text, action).bounds(left, row, 300, 20).build());
        row += 22; return result;
    }

    private void choice(String label, Supplier<Component> value, Runnable change, Runnable reset, BooleanSupplier changed) {
        Button main = addRenderableWidget(Button.builder(Component.empty(), b -> { change.run(); b.setMessage(message(label, value.get(), changed.getAsBoolean())); })
                .bounds(left, row, 252, 20).build());
        main.setMessage(message(label, value.get(), changed.getAsBoolean()));
        Button restore = addRenderableWidget(Button.builder(tr("reset"), b -> { reset.run(); rebuildWidgets(); })
                .bounds(left + 256, row, 44, 20).build());
        // Keep this live after an edit without recreating a text field.
        restore.setTooltip(Tooltip.create(tr("reset_hint")));
        row += 22;
    }

    private Component message(String key, Component value, boolean changed) {
        return Component.literal(changed ? "* " : "").append(tr(key)).append(": ").append(value);
    }

    private void bool(String label, BooleanSupplier value, Runnable change, boolean defaultValue) {
        choice(label, () -> tr(value.getAsBoolean() ? "on" : "off"), change,
                () -> { if (value.getAsBoolean() != defaultValue) change.run(); }, () -> value.getAsBoolean() != defaultValue);
    }

    private void number(String label, IntSupplier value, IntConsumer change, int defaults, int min, int max) {
        Button description = addRenderableWidget(Button.builder(Component.empty(), b -> {})
                .bounds(left, row, 172, 20).build());
        description.active = false;
        description.setMessage(message(label, Component.literal("(" + min + "–" + max + ")"), value.getAsInt() != defaults));
        EditBox box = new EditBox(font, left + 176, row, 76, 20, tr(label));
        box.setMaxLength(5); box.setValue(Integer.toString(value.getAsInt()));
        box.setFilter(s -> s.matches("[0-9]*"));
        box.setResponder(s -> {
            try {
                int number = Integer.parseInt(s);
                boolean valid = number >= min && number <= max;
                box.setTextColor(valid ? 0xFFFFFF : 0xFF6666);
                if (valid) change.accept(number);
                description.setMessage(message(label, Component.literal("(" + min + "–" + max + ")"), value.getAsInt() != defaults));
            } catch (NumberFormatException e) { box.setTextColor(0xFF6666); }
        });
        box.setTooltip(Tooltip.create(tr("number_hint")));
        addRenderableWidget(box);
        addRenderableWidget(Button.builder(tr("reset"), b -> { change.accept(defaults); rebuildWidgets(); })
                .bounds(left + 256, row, 44, 20).build());
        row += 22;
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (capturing != null) {
            if (key != GLFW.GLFW_KEY_ESCAPE) capturing.confirmKey = InputConstants.getKey(key, scan).getName();
            capturing = null; rebuildWidgets(); return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (capturing != null) {
            capturing.confirmKey = InputConstants.Type.MOUSE.getOrCreate(button).getName();
            capturing = null; rebuildWidgets(); return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public void onClose() {
        capturing = null;
        if (!section.equals("home")) { navigate(section.equals("rule") ? "bindings" : section.equals("bindings") ? "custom" : "home"); return; }
        if (ClientConfig.save()) minecraft.setScreen(parent);
        else error = tr("save_failed").getString();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF0202020);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        Component subtitle = section.equals("rule") ? Component.translatable(selectedKey) : tr(section);
        graphics.drawCenteredString(font, subtitle, width / 2, 25, 0xFFCC66);
        if (!error.isEmpty()) graphics.drawCenteredString(font, error, width / 2, height - 42, 0xFF6666);
    }
}
