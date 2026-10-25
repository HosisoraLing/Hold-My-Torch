package dev.holdmytorch;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.slf4j.LoggerFactory;

public final class LightSources {
    private static final List<Method> providers = new ArrayList<>();

    /** Optional adapters: no dynamic-light mod is required to load this class. */
    public static void discover() {
        for (String name : List.of("org.thinkingstudio.ryoamiclights.RyoamicLights",
                "dev.lambdaurora.lambdynlights.LambDynLights", "net.optifine.DynamicLights")) {
            try {
                Class<?> type = Class.forName(name, false, LightSources.class.getClassLoader());
                for (Method method : type.getMethods()) {
                    Class<?>[] args = method.getParameterTypes();
                    if (Modifier.isStatic(method.getModifiers()) && method.getReturnType() == int.class
                            && (method.getName().equals("getLuminanceFromItemStack") || method.getName().equals("getLightLevel"))
                            && (args.length == 1 || (args.length == 2 && args[1] == boolean.class))
                            && args[0] == ItemStack.class) {
                        providers.add(method);
                        LoggerFactory.getLogger("holdmytorch").info("Found optional light provider {}", method);
                        break;
                    }
                }
            } catch (ClassNotFoundException | LinkageError ignored) { /* Optional dependency absent. */ }
        }
    }

    public static boolean protectedItem(ItemStack stack) {
        // Only known placeable BlockItems; never guess whether an arbitrary use() places something.
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        int light = blockItem.getBlock().defaultBlockState().getLightEmission();
        if (ClientConfig.current.dynamicLights) {
            for (var iterator = providers.iterator(); iterator.hasNext();) {
                Method method = iterator.next();
                try {
                    Object value = method.getParameterCount() == 1 ? method.invoke(null, stack) : method.invoke(null, stack, false);
                    light = Math.max(light, (Integer) value);
                } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                    LoggerFactory.getLogger("holdmytorch").warn("Disabling failed light adapter {}; block emission remains active", method, e);
                    iterator.remove();
                }
            }
        }
        return light > ClientConfig.current.brightnessThreshold;
    }
}
