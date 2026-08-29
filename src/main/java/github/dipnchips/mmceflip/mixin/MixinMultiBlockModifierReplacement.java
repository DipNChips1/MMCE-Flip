package github.dipnchips.mmceflip.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.util.EnumFacing;

import hellfirepvp.modularmachinery.common.modifier.MultiBlockModifierReplacement;
import hellfirepvp.modularmachinery.common.tiles.base.TileMultiblockMachineController;
import hellfirepvp.modularmachinery.common.util.BlockArray;

import github.dipnchips.mmceflip.util.ControllerOrientationAccess;
import github.dipnchips.mmceflip.util.Orientation;
import github.dipnchips.mmceflip.util.OrientationPatterns;

/**
 * Multi-block modifier replacements look their structure up by the
 * controller's facing alone, which cannot express the spin. This injection
 * re-runs the tiny matches body against the orientation-aware cache instead,
 * so machines with multi-block modifiers match correctly at any of the 24
 * orientations; the identity orientation keeps the vanilla body.
 */
@Mixin(MultiBlockModifierReplacement.class)
public abstract class MixinMultiBlockModifierReplacement {

    @Shadow(remap = false)
    private BlockArray blockArray;

    @Inject(method = "matches", at = @At("HEAD"), cancellable = true, remap = false)
    private void MMCEFlip$orientationMatches(TileMultiblockMachineController ctrl, CallbackInfoReturnable<Boolean> cir) {
        if (!(ctrl instanceof ControllerOrientationAccess) || ctrl.getControllerRotation() == null) {
            return;
        }
        int spin = ((ControllerOrientationAccess) ctrl).MMCEFlip$getSpin();
        EnumFacing facing = ctrl.getControllerRotation();
        if (facing != EnumFacing.UP && facing != EnumFacing.DOWN && spin == 0) {
            return;
        }
        Orientation orientation = Orientation.of(facing, spin);
        if (orientation.isIdentity()) {
            return;
        }
        BlockArray array = OrientationPatterns.getBlockArray(this.blockArray, orientation);
        cir.setReturnValue(array.matches(ctrl.getWorld(), ctrl.getPos(), false, null));
    }
}
