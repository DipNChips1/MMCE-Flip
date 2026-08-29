package github.dipnchips.mmceflip.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.TaggedPositionBlockArray;
import hellfirepvp.modularmachinery.common.tiles.base.TileMultiblockMachineController;
import hellfirepvp.modularmachinery.common.util.BlockArrayCache;

import github.dipnchips.mmceflip.util.AssemblyOrientationContext;
import github.dipnchips.mmceflip.util.ControllerOrientationAccess;
import github.dipnchips.mmceflip.util.Orientation;
import github.dipnchips.mmceflip.util.OrientationPatterns;

/**
 * Teaches the assembly stick (Modular Machinery's creative auto-build) to
 * build the structure matching the controller's full orientation, including
 * the spin, instead of only the facing-keyed spin-zero variant.
 *
 * The pattern is resolved orientation-aware into a context slot before the
 * original method runs, and the cache lookup redirect picks it up from
 * there; everything else in the assembly flow (ingredient collection,
 * creative instant-build, partial assembly) is untouched.
 *
 * Machines with dynamic patterns (expandable structures) are canceled with
 * an explanatory message instead: their pattern assembly looks sub-patterns
 * up by facing alone, which returns {@code null} for vertical facings and
 * would crash the assembly on the network thread.
 *
 * Identity-orientation controllers keep the completely vanilla behavior.
 */
@Mixin(ink.ikx.mmce.core.AssemblyEventHandler.class)
public abstract class MixinAssemblyEventHandler {

    @Inject(method = "assemblyBefore", at = @At("HEAD"), cancellable = true, remap = false)
    private static void MMCEFlip$prepareOrientationOverride(DynamicMachine machine, EntityPlayer player, BlockPos pos, int dynamicPatternSize, CallbackInfo ci) {
        AssemblyOrientationContext.clear();
        if (machine == null || player.world.isRemote) {
            return;
        }

        TileEntity te = player.world.getTileEntity(pos);
        if (!(te instanceof ControllerOrientationAccess) || !(te instanceof TileMultiblockMachineController)) {
            return;
        }

        EnumFacing facing = player.world.getBlockState(pos).getValue(BlockController.FACING);
        int spin = ((ControllerOrientationAccess) te).MMCEFlip$getSpin();
        if (facing != EnumFacing.UP && facing != EnumFacing.DOWN && spin == 0) {
            return;
        }

        Orientation orientation = Orientation.of(facing, spin);

        if (!machine.getDynamicPatterns().isEmpty()) {
            player.sendStatusMessage(new TextComponentTranslation("message.mmceflip.assembly.unsupported"), true);
            ci.cancel();
            return;
        }

        AssemblyOrientationContext.setOverride(OrientationPatterns.getPattern(machine.getPattern(), orientation), orientation);
    }

    @Redirect(
            method = "assemblyBefore",
            at = @At(value = "INVOKE", target = "Lhellfirepvp/modularmachinery/common/util/BlockArrayCache;getBlockArrayCache(Lhellfirepvp/modularmachinery/common/machine/TaggedPositionBlockArray;Lnet/minecraft/util/EnumFacing;)Lhellfirepvp/modularmachinery/common/machine/TaggedPositionBlockArray;"),
            remap = false
    )
    private static TaggedPositionBlockArray MMCEFlip$orientationAwareLookup(TaggedPositionBlockArray blockArray, EnumFacing facing) {
        if (AssemblyOrientationContext.hasOverride()) {
            return AssemblyOrientationContext.getOverride();
        }
        return BlockArrayCache.getBlockArrayCache(blockArray, facing);
    }

    @Inject(method = "assemblyBefore", at = @At("TAIL"), remap = false)
    private static void MMCEFlip$clearOrientationOverride(DynamicMachine machine, EntityPlayer player, BlockPos pos, int dynamicPatternSize, CallbackInfo ci) {
        AssemblyOrientationContext.clear();
    }
}
