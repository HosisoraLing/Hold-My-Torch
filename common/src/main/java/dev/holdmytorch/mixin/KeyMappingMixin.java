package dev.holdmytorch.mixin;

import dev.holdmytorch.InputFilter;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {
    @Shadow private int clickCount;

    @Inject(method = "setDown", at = @At("HEAD"))
    private void holdmytorch$raw(boolean down, CallbackInfo ci) {
        InputFilter.raw((KeyMapping) (Object) this, down);
    }

    @Inject(method = "isDown", at = @At("RETURN"), cancellable = true)
    private void holdmytorch$down(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(InputFilter.down((KeyMapping) (Object) this, cir.getReturnValue()));
    }

    @Inject(method = "consumeClick", at = @At("HEAD"), cancellable = true)
    private void holdmytorch$click(CallbackInfoReturnable<Boolean> cir) {
        Boolean result = InputFilter.consume((KeyMapping) (Object) this, clickCount);
        if (result != null) {
            clickCount = 0; // Drain OS repeat and blocked clicks; only the gesture emits a click.
            cir.setReturnValue(result);
        }
    }
}
