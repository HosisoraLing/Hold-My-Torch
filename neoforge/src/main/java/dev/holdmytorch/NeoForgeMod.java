package dev.holdmytorch;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "holdmytorch", dist = Dist.CLIENT)
public final class NeoForgeMod {
    public NeoForgeMod(ModContainer container) {
        ClientConfig.load(FMLPaths.CONFIGDIR.get());
        LightSources.discover();
        NeoForge.EVENT_BUS.addListener(NeoForgeMod::input);
        NeoForge.EVENT_BUS.addListener(NeoForgeMod::render);
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new ConfigScreen(parent));
    }
    private static void input(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isUseItem() && InputFilter.blockUse(event.getHand())) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }
    private static void render(RenderGuiEvent.Post event) { GuardNotice.render(event.getGuiGraphics()); }
}
