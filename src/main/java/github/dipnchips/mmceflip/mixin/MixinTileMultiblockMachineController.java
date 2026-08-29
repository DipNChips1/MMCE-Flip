package github.dipnchips.mmceflip.mixin;

import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import hellfirepvp.modularmachinery.common.modifier.SingleBlockModifierReplacement;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.TaggedPositionBlockArray;
import hellfirepvp.modularmachinery.common.tiles.base.TileMultiblockMachineController;

import github.dipnchips.mmceflip.util.ControllerOrientationAccess;
import github.dipnchips.mmceflip.util.Orientation;
import github.dipnchips.mmceflip.util.OrientationPatterns;

/**
 * Teaches the machine controller tile to work with all 24 cube
 * orientations: the blockstate facing combined with a spin of 0-3 around
 * that facing axis.
 *
 * Modular Machinery's own rotation logic only walks the four horizontal
 * facings with no spin; feeding it anything else would spin its
 * {@code while (facing != target)} loops forever. These injections intercept
 * every such path: non-identity orientations are matched against the
 * orientation cache built by {@code MixinBlockArrayCache}, while the
 * identity orientation (facing north, no spin) keeps using the completely
 * untouched vanilla code paths.
 *
 * Machines that use dynamic patterns are deliberately rejected for
 * non-identity orientations, because dynamic pattern matching has its own
 * horizontal-only rotation loops.
 */
@Mixin(TileMultiblockMachineController.class)
public abstract class MixinTileMultiblockMachineController extends TileEntity implements ControllerOrientationAccess {

    @Shadow
    protected EnumFacing controllerRotation;

    @Shadow
    protected DynamicMachine.ModifierReplacementMap foundReplacements;

    @Shadow
    protected DynamicMachine foundMachine;

    @Shadow
    protected TaggedPositionBlockArray foundPattern;

    @Shadow
    @Final
    protected Map<String, List<RecipeModifier>> foundModifiers;

    @Shadow
    protected abstract void resetMachine(boolean clearData);

    @Unique
    private int MMCEFlip$spin = 0;

    @Override
    public int MMCEFlip$getSpin() {
        return MMCEFlip$spin;
    }

    @Override
    public void MMCEFlip$setSpin(int spin) {
        this.MMCEFlip$spin = ((spin % 4) + 4) % 4;
    }

    @Override
    public void MMCEFlip$onOrientationChanged() {
        // Mirror breaking and replacing the controller: drop any formed
        // structure so the next structure check re-discovers the machine
        // for the new orientation instead of trusting the stale pattern.
        resetMachine(true);
        // Push the new orientation to clients so the model renderer and
        // any other client-side logic see the new facing and spin.
        ((hellfirepvp.modularmachinery.common.tiles.base.TileEntitySynchronized) (Object) this).markForUpdateSync();
    }

    @Unique
    private Orientation MMCEFlip$orientation() {
        return Orientation.of(this.controllerRotation == null ? EnumFacing.NORTH : this.controllerRotation, MMCEFlip$spin);
    }

    /**
     * True when the controller is in a state Modular Machinery handles
     * natively: any horizontal facing with no spin. All injections fall
     * through to the untouched vanilla code in that case.
     */
    @Unique
    private static boolean MMCEFlip$isVanillaScope(EnumFacing facing, int spin) {
        return spin == 0 && facing != null && facing != EnumFacing.UP && facing != EnumFacing.DOWN;
    }

    /**
     * Non-identity branch of the formation funnel. Cancels the vanilla body
     * and matches the pattern from the orientation cache against rotated
     * modifier replacements; the pattern passed in by the caller is only
     * keyed by facing, so it is re-fetched here.
     */
    @Inject(method = "matchesRotation", at = @At("HEAD"), cancellable = true, remap = false)
    private void MMCEFlip$orientationMatchesRotation(TaggedPositionBlockArray pattern, DynamicMachine machine, EnumFacing ctrlRotation,
                                                       CallbackInfoReturnable<Boolean> cir) {
        if (MMCEFlip$isVanillaScope(ctrlRotation, MMCEFlip$spin)) {
            return;
        }
        Orientation orientation = Orientation.of(ctrlRotation == null ? EnumFacing.NORTH : ctrlRotation, MMCEFlip$spin);
        if (orientation.isIdentity()) {
            return;
        }
        if (machine == null || !machine.getDynamicPatterns().isEmpty()) {
            resetMachine(false);
            cir.setReturnValue(false);
            return;
        }

        TaggedPositionBlockArray oriented = OrientationPatterns.getPattern(machine.getPattern(), orientation);
        if (!world.isAreaLoaded(oriented.getPatternBoundingBox(pos))) {
            cir.setReturnValue(false);
            return;
        }

        DynamicMachine.ModifierReplacementMap replacements =
                OrientationPatterns.rotateReplacements(machine.getModifiersAsMatchingReplacements(), orientation);
        if (oriented.matches(world, pos, false, replacements)) {
            this.foundPattern = oriented;
            this.foundMachine = machine;
            this.foundReplacements = replacements;
            cir.setReturnValue(true);
        } else {
            resetMachine(false);
            cir.setReturnValue(false);
        }
    }

    /**
     * Non-identity branch of the single-block modifier re-check. Cancels the
     * vanilla body (whose rotation loop assumes horizontal facings) and
     * applies the orientation transform to modifier offsets and their block
     * information.
     */
    @Inject(method = "updateModifiers", at = @At("HEAD"), cancellable = true, remap = false)
    private void MMCEFlip$orientationUpdateModifiers(CallbackInfo ci) {
        if (this.controllerRotation == null || MMCEFlip$isVanillaScope(this.controllerRotation, MMCEFlip$spin)) {
            return;
        }
        Orientation orientation = MMCEFlip$orientation();
        for (Map.Entry<BlockPos, List<SingleBlockModifierReplacement>> entry : this.foundMachine.getModifiers().entrySet()) {
            BlockPos realAt = pos.add(orientation.apply(entry.getKey()));
            for (SingleBlockModifierReplacement mod : entry.getValue()) {
                if (OrientationPatterns.rotateReplacement(mod, orientation).matches(world, realAt, true)) {
                    foundModifiers.put(mod.getModifierName(), mod.getModifiers());
                }
            }
        }
        ci.cancel();
    }

    /**
     * Vanilla serialises the controller rotation as a horizontal index, which
     * is {@code -1} for vertical facings. Write a horizontal placeholder in
     * that legacy tag and remember the true facing under our own key.
     */
    @Redirect(method = "writeCustomNBT", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/EnumFacing;getHorizontalIndex()I"))
    private int MMCEFlip$writeLegacyRotation(EnumFacing facing) {
        if (facing == EnumFacing.UP || facing == EnumFacing.DOWN) {
            return 0;
        }
        return facing.getHorizontalIndex();
    }

    @Inject(method = "writeCustomNBT", at = @At("TAIL"), remap = false)
    private void MMCEFlip$writeOrientation(NBTTagCompound compound, CallbackInfo ci) {
        if (this.controllerRotation != null && (this.controllerRotation == EnumFacing.UP || this.controllerRotation == EnumFacing.DOWN)) {
            compound.setByte("mmceflip_facing", (byte) this.controllerRotation.getIndex());
        }
        if (MMCEFlip$spin != 0) {
            compound.setByte("mmceflip_spin", (byte) MMCEFlip$spin);
        }
    }

    /**
     * Reads the spin even when no machine is formed, so an unformed
     * controller keeps its spin across save and reload. On the client, a
     * spin change also marks the block for re-render, so the spin-aware
     * block model bakes fresh quads.
     */
    @Inject(method = "readCustomNBT", at = @At("TAIL"), remap = false)
    private void MMCEFlip$readSpin(NBTTagCompound compound, CallbackInfo ci) {
        int received = compound.hasKey("mmceflip_spin") ? compound.getByte("mmceflip_spin") & 0xFF : 0;
        received = ((received % 4) + 4) % 4;
        if (received == MMCEFlip$spin) {
            return;
        }
        MMCEFlip$setSpin(received);
        if (world != null && world.isRemote) {
            world.markBlockRangeForRenderUpdate(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
        }
    }

    /**
     * After the vanilla restore ran with facing-keyed patterns, re-align the
     * restored pattern and replacements with the true orientation.
     */
    @Inject(method = "readMachineNBT", at = @At("TAIL"), remap = false)
    private void MMCEFlip$readOrientation(NBTTagCompound compound, CallbackInfo ci) {
        if (this.foundMachine == null || this.controllerRotation == null) {
            return;
        }
        if (MMCEFlip$isVanillaScope(this.controllerRotation, MMCEFlip$spin) && !compound.hasKey("mmceflip_facing")) {
            return;
        }
        if (compound.hasKey("mmceflip_facing")) {
            EnumFacing facing = EnumFacing.byIndex(compound.getByte("mmceflip_facing"));
            if (facing != EnumFacing.UP && facing != EnumFacing.DOWN) {
                return;
            }
            this.controllerRotation = facing;
        }
        Orientation orientation = MMCEFlip$orientation();
        if (orientation.isIdentity() || !this.foundMachine.getDynamicPatterns().isEmpty()) {
            return;
        }
        TaggedPositionBlockArray pattern = OrientationPatterns.getPattern(this.foundMachine.getPattern(), orientation);
        if (pattern == null) {
            resetMachine(false);
            return;
        }
        this.foundPattern = pattern;
        this.foundReplacements = OrientationPatterns.rotateReplacements(this.foundMachine.getModifiersAsMatchingReplacements(), orientation);
    }
}
