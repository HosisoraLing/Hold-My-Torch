package dev.holdmytorch;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ToggleKeyMapping;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

public final class InputFilter {
    private static final Map<String, Gesture> gestures = new HashMap<>();
    private static boolean useDown;
    private static final class Gesture {
        final Confirmation confirmation = new Confirmation();
        final ClientConfig.Rule rule;
        final String context;
        boolean down;
        boolean notified;
        Gesture(ClientConfig.Rule rule, String context) { this.rule = rule; this.context = context; }
    }

    public static boolean playing() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.player != null && mc.level != null && mc.screen == null && mc.isWindowActive();
    }

    public static void reset() { gestures.clear(); useDown = false; GuardNotice.clear(); }

    private static ClientConfig.Rule rule(KeyMapping key) {
        if (!playing()) return null;
        // ToggleKeyMapping overrides isDown and interprets setDown as a toggle, not a physical edge.
        if (key instanceof ToggleKeyMapping) return null;
        ClientConfig config = ClientConfig.current;
        ClientConfig.Rule custom = config.customEnabled ? config.keys.get(key.getName()) : null;
        if (custom != null && custom.enabled) return custom;
        Minecraft mc = Minecraft.getInstance();
        if (key == mc.options.keyDrop && config.drop.enabled) return config.drop;
        return null;
    }

    private static boolean placementCandidate(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (hand == InteractionHand.MAIN_HAND && !ClientConfig.current.protectMainHand) return false;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || mc.player.isSpectator()) return false;
        if (!LightSources.protectedItem(mc.player.getItemInHand(hand))) return false;
        // A non-light main hand already had its chance to interact with this block.
        // Never bypass offhand protection just because that interaction returned PASS.
        if (hand == InteractionHand.OFF_HAND) return true;
        if (mc.player.isSecondaryUseActive()) return true;
        Block block = mc.level.getBlockState(hit.getBlockPos()).getBlock();
        // Let modded block interactions run normally, including inherited implementations.
        if (!BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft"))
            return !BlockInteractions.hasOverride(block.getClass());
        // A custom BlockItem may implement onItemUseFirst before block interaction.
        if (!BuiltInRegistries.ITEM.getKey(mc.player.getItemInHand(hand).getItem()).getNamespace().equals("minecraft")) return true;
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
        return gesture(key.getName(), rule, rule == null ? null : context(key));
    }

    private static Gesture gesture(String name, ClientConfig.Rule rule, String context) {
        if (rule == null) { gestures.remove(name); return null; }
        Gesture value = gestures.get(name);
        if (value == null || value.rule != rule || !value.context.equals(context)) {
            value = new Gesture(rule, context);
            gestures.put(name, value);
        }
        return value;
    }

    private static Gesture torchGesture() {
        if (!playing()) return null;
        Minecraft mc = Minecraft.getInstance();
        ClientConfig config = ClientConfig.current;
        ClientConfig.Rule custom = config.customEnabled ? config.keys.get(mc.options.keyUse.getName()) : null;
        boolean enabled = config.torch.enabled && (custom == null || !custom.enabled)
                && ((config.protectMainHand && LightSources.protectedItem(mc.player.getMainHandItem()))
                || LightSources.protectedItem(mc.player.getOffhandItem()));
        return gesture("holdmytorch:torch", enabled ? config.torch : null, context(mc.options.keyUse));
    }

    /** Filter the loader's per-hand input event, before that hand can interact or send use packets. */
    public static boolean blockUse(InteractionHand hand) {
        Gesture value = torchGesture();
        if (value == null || !placementCandidate(hand)) return false;
        Minecraft mc = Minecraft.getInstance();
        // A held eat/drink/charge/block input must never turn into an offhand light placement.
        if (hand == InteractionHand.OFF_HAND && !mc.player.getMainHandItem().isEmpty()
                && (mc.player.getMainHandItem().getUseAnimation() != UseAnim.NONE
                || mc.player.getMainHandItem().getFoodProperties(mc.player) != null)) {
            notice(value, "holdmytorch.torch", "holdmytorch.notice.offhand");
            return true;
        }
        update(value);
        boolean click = value.confirmation.consumeClick();
        boolean blocked = !click && !value.confirmation.isDown();
        if (blocked) notice(value, "holdmytorch.torch", !value.down && useDown ? "holdmytorch.notice.repress" : null);
        else GuardNotice.clear("holdmytorch.torch");
        return blocked;
    }

    public static void raw(KeyMapping key, boolean down) {
        Gesture value = gesture(key);
        if (value != null) {
            if (down && !value.down) value.notified = false;
            value.down = down; update(value);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.options != null && key == mc.options.keyUse) {
            boolean rising = down && !useDown;
            useDown = down;
            Gesture torch = torchGesture();
            if (torch != null) {
                if (rising) torch.notified = false;
                // A changed hand/context while held must wait for a fresh physical press.
                if (rising || !down) torch.down = down;
                update(torch);
            }
        }
    }

    private static void notice(Gesture value, String action, String reason) {
        if (!value.notified) { GuardNotice.show(action, value.rule, reason); value.notified = true; }
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
    public static Boolean consume(KeyMapping key, int originalClicks) {
        Gesture value = gesture(key);
        if (value == null) return null;
        update(value);
        boolean active = key.isConflictContextAndModifierActive();
        boolean allowed = value.confirmation.consumeClick() && active;
        if (!allowed && active && (value.down || originalClicks > 0)) notice(value, key.getName(), null);
        if (allowed) GuardNotice.clear(key.getName());
        return allowed;
    }

    public static boolean down(KeyMapping key, boolean original) {
        Gesture value = gesture(key);
        if (value == null) return original;
        update(value);
        boolean allowed = original && value.confirmation.isDown();
        if (original && !allowed) notice(value, key.getName(), null);
        if (allowed) GuardNotice.clear(key.getName());
        return allowed;
    }

    public static void tick() {
        if (!playing()) reset();
        else torchGesture(); // Invalidate confirmation when either hand changes, even mid-use.
    }
}
