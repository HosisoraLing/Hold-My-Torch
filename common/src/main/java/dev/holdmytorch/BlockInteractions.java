package dev.holdmytorch;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.LoggerFactory;

final class BlockInteractions {
    // Discover runtime names by signature: Forge remaps use(), NeoForge splits it in two.
    private static final List<Method> hooks = Arrays.stream(BlockBehaviour.class.getDeclaredMethods())
            .filter(method -> {
                Class<?>[] args = method.getParameterTypes();
                return Arrays.equals(args, new Class<?>[]{BlockState.class, Level.class, BlockPos.class,
                        Player.class, InteractionHand.class, BlockHitResult.class})
                        || Arrays.equals(args, new Class<?>[]{BlockState.class, Level.class, BlockPos.class,
                        Player.class, BlockHitResult.class})
                        || Arrays.equals(args, new Class<?>[]{ItemStack.class, BlockState.class, Level.class,
                        BlockPos.class, Player.class, InteractionHand.class, BlockHitResult.class});
            }).toList();

    private static final ClassValue<Boolean> overrides = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> blockType) {
            try {
                for (Class<?> type = blockType; type != null && type != BlockBehaviour.class; type = type.getSuperclass()) {
                    for (Method hook : hooks) {
                        try {
                            type.getDeclaredMethod(hook.getName(), hook.getParameterTypes());
                            return true;
                        } catch (NoSuchMethodException ignored) { /* Check inherited implementations too. */ }
                    }
                }
            } catch (LinkageError | SecurityException e) {
                LoggerFactory.getLogger("holdmytorch").warn("Cannot inspect block interaction {}; confirmation remains required", blockType, e);
            }
            return false;
        }
    };

    // Inspection only: never invoke an interaction to predict whether it will consume the click.
    static boolean hasOverride(Class<?> blockType) { return overrides.get(blockType); }
}
