package dev.holdmytorch.mixin;

import dev.holdmytorch.InputFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void holdmytorch$clearOnScreenOrFocusChange(CallbackInfo ci) { InputFilter.tick(); }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void holdmytorch$screen(Screen screen, CallbackInfo ci) { InputFilter.reset(); }

    @Inject(method = "setWindowActive", at = @At("HEAD"))
    private void holdmytorch$focus(boolean active, CallbackInfo ci) { if (!active) InputFilter.reset(); }
}
