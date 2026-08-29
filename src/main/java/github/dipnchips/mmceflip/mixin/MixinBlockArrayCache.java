package github.dipnchips.mmceflip.mixin;

import java.util.Collection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import github.dipnchips.mmceflip.MMCEFlip;
import github.dipnchips.mmceflip.util.OrientationPatterns;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.util.BlockArrayCache;

/**
 * Extends the structure cache built by Modular Machinery with rotated
 * variants of every machine pattern and multi-block modifier replacement for
 * all 24 cube orientations.
 *
 * The full set lives in {@link OrientationPatterns}' own cache; the vertical
 * spin-zero entries are additionally mirrored into Modular Machinery's
 * facing-keyed cache so controllers facing up or down never hit a null
 * pattern, even in code this mod does not intercept.
 */
@Mixin(BlockArrayCache.class)
public abstract class MixinBlockArrayCache {

    @Inject(method = "buildCache", at = @At("TAIL"), remap = false)
    private static void MMCEFlip$addOrientationCacheEntries(Collection<DynamicMachine> machines, CallbackInfo ci) {
        int entries = 0;
        for (DynamicMachine machine : machines) {
            entries += OrientationPatterns.prebuildMachine(machine);
        }
        MMCEFlip.logger.info("Orientation cache ready: {} rotated entries across {} machines", entries, machines.size());
    }
}
