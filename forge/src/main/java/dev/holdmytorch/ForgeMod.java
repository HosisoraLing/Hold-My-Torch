package dev.holdmytorch;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;

@Mod("holdmytorch")
public final class ForgeMod {
    public ForgeMod() { DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> Client::init); }
    private static final class Client {
        static void init() {
            ClientConfig.load(FMLPaths.CONFIGDIR.get());
            LightSources.discover();
            MinecraftForge.EVENT_BUS.addListener(Client::input);
            ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new ConfigScreen(parent)));
        }
        static void input(InputEvent.InteractionKeyMappingTriggered event) {
            if (event.isUseItem() && InputFilter.blockUse(event.getHand())) {
                event.setCanceled(true);
                event.setSwingHand(false);
            }
        }
    }
}
