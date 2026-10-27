package dev.holdmytorch;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ToggleKeyMapping;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

public final class InputFilter {
    private static final Map<String, Gesture> gestures = new HashMap<>();
    private static final class Gesture {
        final Confirmation confirmation = new Confirmation();
        final ClientConfig.Rule rule;
        final String context;
        boolean down;
        Gesture(ClientConfig.Rule rule, String context) { this.rule = rule; this.context = context; }
    }

    public static boolean playing() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.player != null && mc.level != null && mc.screen == null && mc.isWindowActive();
    }

    public static void reset() { gestures.clear(); }

    private static ClientConfig.Rule rule(KeyMapping key) {
        if (!playing()) return null;
        // ToggleKeyMapping overrides isDown and interprets setDown as a toggle, not a physical edge.
        if (key instanceof ToggleKeyMapping) return null;
        ClientConfig config = ClientConfig.current;
        ClientConfig.Rule custom = config.customEnabled ? config.keys.get(key.getName()) : null;
        if (custom != null && custom.enabled) return custom;
        Minecraft mc = Minecraft.getInstance();
        if (key == mc.options.keyDrop && config.drop.enabled) return config.drop;
        if (key == mc.options.keyUse && config.torch.enabled && placementCandidate()) return config.torch;
        return null;
    }

    public static boolean placementCandidate() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || mc.player.isSpectator()) return false;
        if (!LightSources.protectedItem(mc.player.getMainHandItem())
                && !LightSources.protectedItem(mc.player.getOffhandItem())) return false;
        if (mc.player.isSecondaryUseActive()) return true;
        Block block = mc.level.getBlockState(hit.getBlockPos()).getBlock();
        // Let modded block interactions run normally, including inherited implementations.
        if (!BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft"))
            return !BlockInteractions.hasOverride(block.getClass());
        // A custom BlockItem may implement onItemUseFirst before block interaction.
        if ((LightSources.protectedItem(mc.player.getMainHandItem()) && !BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()).getNamespace().equals("minecraft"))
                || (LightSources.protectedItem(mc.player.getOffhandItem()) && !BuiltInRegistries.ITEM.getKey(mc.player.getOffhandItem().getItem()).getNamespace().equals("minecraft"))) return true;
        // Do not call use/useItemOn speculatively: those methods can mutate the world.
        Class<?> type = block.getClass();
        return !((type == DoorBlock.class && block != Blocks.IRON_DOOR)
                || (type == TrapDoorBlock.class && block != Blocks.IRON_TRAPDOOR)
                || type == FenceGateBlock.class || type == ChestBlock.class || type == EnderChestBlock.class
                || type == BarrelBlock.class || type == ShulkerBoxBlock.class || type == CraftingTableBlock.class
                || type == FurnaceBlock.class || type == BlastFurnaceBlock.class || type == SmokerBlock.class
                || type == LeverBlock.class || type == ButtonBlock.class || type == StonecutterBlock.class
                || type == CartographyTableBlock.class || type == LoomBlock.class || type == SmithingTableBlock.class
                || type == GrindstoneBlock.class || type == AnvilBlock.class || block == Blocks.ENCHANTING_TABLE
                || type == HopperBlock.class || type == DispenserBlock.class || type == DropperBlock.class
                || type == BrewingStandBlock.class);
    }

    private static String context(KeyMapping key) {
        Minecraft mc = Minecraft.getInstance();
        if (key != mc.options.keyDrop && key != mc.options.keyUse) return key.saveString();
        // Changing the selected item or either hand invalidates an armed gesture.
        return key.saveString() + ":" + mc.player.getInventory().selected + ":"
                + BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()) + ":"
                + BuiltInRegistries.ITEM.getKey(mc.player.getOffhandItem().getItem());
    }

    private static Gesture gesture(KeyMapping key) {
        ClientConfig.Rule rule = rule(key);
        String name = key.getName();
        if (rule == null) { gestures.remove(name); return null; }
        String context = context(key);
        Gesture value = gestures.get(name);
        if (value == null || value.rule != rule || !value.context.equals(context)) {
            value = new Gesture(rule, context);
            gestures.put(name, value);
        }
        return value;
    }

    public static void raw(KeyMapping key, boolean down) {
        Gesture value = gesture(key);
        if (value != null) { value.down = down; update(value); }
    }

    private static void update(Gesture value) {
        ClientConfig.Rule rule = value.rule;
        value.confirmation.update(value.down, physical(InputConstants.getKey(rule.confirmKey)),
                System.nanoTime() / 1_000_000, rule.mode, rule.holdMs, rule.doubleMs);
    }

    public static boolean physical(InputConstants.Key key) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE)
            return key.getValue() >= 0 && key.getValue() <= GLFW.GLFW_MOUSE_BUTTON_LAST
                    && GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        if (key.getType() == InputConstants.Type.KEYSYM)
            return key.getValue() >= 0 && key.getValue() <= GLFW.GLFW_KEY_LAST && InputConstants.isKeyDown(window, key.getValue());
        return false; // GLFW cannot poll scan codes; keyboard capture normally supplies a KEYSYM.
    }

    /** null leaves the original key mapping untouched, including GUI behavior. */
    public static Boolean consume(KeyMapping key) {
        Gesture value = gesture(key);
        if (value == null) return null;
        update(value);
        return value.confirmation.consumeClick() && key.isConflictContextAndModifierActive();
    }

    public static boolean down(KeyMapping key, boolean original) {
        Gesture value = gesture(key);
        if (value == null) return original;
        update(value);
        return original && value.confirmation.isDown();
    }

    public static void tick() {
        if (!playing()) reset();
    }
}
