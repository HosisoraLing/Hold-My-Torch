package dev.holdmytorch;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = "holdmytorch", dist = Dist.CLIENT)
public final class NeoForgeMod {
    public NeoForgeMod(ModContainer container) {
        ClientConfig.load(FMLPaths.CONFIGDIR.get());
        LightSources.discover();
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new ConfigScreen(parent));
    }
}
