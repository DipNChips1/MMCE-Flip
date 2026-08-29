package github.dipnchips.mmceflip.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.TaggedPositionBlockArray;
import hellfirepvp.modularmachinery.common.tiles.base.TileMultiblockMachineController;
import hellfirepvp.modularmachinery.common.util.BlockArray;
import hellfirepvp.modularmachinery.common.util.BlockArrayCache;

import github.dipnchips.mmceflip.util.AssemblyOrientationContext;
import github.dipnchips.mmceflip.util.ControllerOrientationAccess;
import github.dipnchips.mmceflip.util.Orientation;
import github.dipnchips.mmceflip.util.OrientationPatterns;

/**
 * Teaches MMCE-Addons' advanced machine assembler and disassembler to work
 * with the controller's full orientation (facing plus spin) instead of only
 * the facing-keyed variant.
 *
 * The pattern is resolved orientation-aware into the shared context slot
 * before the original method runs; the facing-only cache lookup redirect
 * picks the oriented pattern up from there, and the modifier-position
 * rotation redirect maps single-block modifiers with the same full
 * transform instead of the builder's horizontal-only rotation.
 *
 * Machines with dynamic patterns are canceled with an explanatory
 * message instead: their expansion sections have no fixed positions to
 * resolve for a vertical or spun controller.
 *
 * Identity-orientation controllers keep the completely vanilla behavior.
 * The whole mixin is {@link Pseudo}: it does nothing when MMCE-Addons is
 * absent.
 */
@Pseudo
@Mixin(targets = "github.alecsio.mmceaddons.common.item.BaseItemAdvancedMachineBuilder", remap = false)
public abstract class MixinAdvancedMachineBuilder {

    @Inject(method = "onControllerRightClick", at = @At("HEAD"), cancellable = true, remap = false)
    private void MMCEFlip$prepareOrientationOverride(EntityPlayer player, BlockPos controllerPos, World world, CallbackInfoReturnable<Boolean> cir) {
        AssemblyOrientationContext.clear();
        if (player.world.isRemote) {
            return;
        }

        TileEntity te = world.getTileEntity(controllerPos);
        if (!(te instanceof ControllerOrientationAccess) || !(te instanceof TileMultiblockMachineController)) {
            return;
        }

        EnumFacing facing = world.getBlockState(controllerPos).getValue(BlockController.FACING);
        int spin = ((ControllerOrientationAccess) te).MMCEFlip$getSpin();
        if (facing != EnumFacing.UP && facing != EnumFacing.DOWN && spin == 0) {
            return;
        }

        // Resolve the machine exactly like the builder itself does: blueprint
        // slot first, then the controller block's parent machine. Unformed
        // controllers have neither foundMachine nor blueprint set, so the
        // block fallback is the one that matters while building.
        DynamicMachine machine = ((TileMultiblockMachineController) te).getBlueprintMachine();
        if (machine == null) {
            net.minecraft.block.Block block = world.getBlockState(controllerPos).getBlock();
            if (block instanceof BlockController) {
                machine = ((BlockController) block).getParentMachine();
            } else if (block instanceof hellfirepvp.modularmachinery.common.block.BlockFactoryController) {
                machine = ((hellfirepvp.modularmachinery.common.block.BlockFactoryController) block).getParentMachine();
            }
        }
        if (machine == null) {
            return;
        }

        if (!machine.getDynamicPatterns().isEmpty()) {
            player.sendStatusMessage(new TextComponentTranslation("message.mmceflip.assembly.unsupported"), true);
            cir.setReturnValue(true);
            return;
        }

        Orientation orientation = Orientation.of(facing, spin);
        AssemblyOrientationContext.setOverride(OrientationPatterns.getPattern(machine.getPattern(), orientation), orientation);
    }

    @Redirect(
            method = "onControllerRightClick",
            at = @At(value = "INVOKE", target = "Lhellfirepvp/modularmachinery/common/util/BlockArrayCache;getBlockArrayCache(Lhellfirepvp/modularmachinery/common/machine/TaggedPositionBlockArray;Lnet/minecraft/util/EnumFacing;)Lhellfirepvp/modularmachinery/common/machine/TaggedPositionBlockArray;"),
            remap = false
    )
    private TaggedPositionBlockArray MMCEFlip$orientationAwareLookup(TaggedPositionBlockArray blockArray, EnumFacing facing) {
        if (AssemblyOrientationContext.hasOverride()) {
            return AssemblyOrientationContext.getOverride();
        }
        return BlockArrayCache.getBlockArrayCache(blockArray, facing);
    }

    /**
     * The builder maps each single-block modifier's canonical position into
     * the rotated pattern with a horizontal-only {@link Rotation} before
     * merging the modifier's block states into the pattern. While the
     * orientation override is active, map it with the full orientation
     * instead, which lands it on the same position the oriented pattern
     * already uses.
     */
    @Redirect(
            method = "onControllerRightClick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/BlockPos;rotate(Lnet/minecraft/util/Rotation;)Lnet/minecraft/util/math/BlockPos;",
                    remap = true),
            remap = false
    )
    private BlockPos MMCEFlip$orientationAwareModifierPos(BlockPos modifierPos, Rotation original) {
        Orientation orientation = AssemblyOrientationContext.getOrientation();
        if (orientation != null) {
            return orientation.apply(modifierPos);
        }
        return modifierPos.rotate(original);
    }

    @Inject(method = "onControllerRightClick", at = @At("TAIL"), remap = false)
    private void MMCEFlip$clearOrientationOverride(EntityPlayer player, BlockPos controllerPos, World world, CallbackInfoReturnable<Boolean> cir) {
        AssemblyOrientationContext.clear();
    }
}
