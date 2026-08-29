package github.dipnchips.mmceflip.mixin;

import java.util.Map;
import java.util.WeakHashMap;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.util.EnumFacing;

import hellfirepvp.modularmachinery.common.tiles.base.TileMultiblockMachineController;

import github.kasuminova.mmce.client.renderer.GeoModelRenderTask;
import github.kasuminova.mmce.client.renderer.MachineControllerRenderer;
import github.kasuminova.mmce.client.util.MatrixStack;

import github.dipnchips.mmceflip.util.ControllerOrientationAccess;
import github.dipnchips.mmceflip.util.Orientation;

/**
 * Renders machine controller models at the controller's full orientation.
 *
 * The baked model buffers are rotated with {@code rotateBlockMatrix}, which
 * only understands the four horizontal facings; vertical facings silently
 * skip rotation and the spin does not exist to it at all. This mixin
 * replaces that step for every non-vanilla orientation: the facing lookup is
 * redirected to capture the tile (and its spin), vanilla's horizontal-only
 * rotation is neutralized, and the complete orientation is applied with the
 * matrix stack's axis rotations. Horizontal facings without spin keep the
 * untouched vanilla path.
 *
 * The rotation decomposition matches the structure rotation exactly: the
 * facing rotation {@code F} from {@link Orientation}'s tables, followed by
 * the spin, which in the model's local frame is a rotation about the model
 * north axis (the pre-image of the facing axis under {@code F}).
 *
 * Baked buffers are cached per tile and only rebuilt on model changes, so
 * the per-frame render entry also evicts a tile's task when its orientation
 * changed, forcing a full rebake on the next frame.
 */
@Mixin(MachineControllerRenderer.class)
public abstract class MixinMachineControllerRenderer {

    private static final float MMCEFlip_HALF_PI = (float) (Math.PI / 2);
    private static final float MMCEFlip_PI = (float) Math.PI;

    @Shadow
    @Final
    protected static ThreadLocal<MatrixStack> MATRIX_STACK;

    @Shadow
    @Final
    protected Map<TileMultiblockMachineController, GeoModelRenderTask> tasks;

    @Unique
    private static final ThreadLocal<Orientation> MMCEFlip_PENDING_ROTATION = new ThreadLocal<>();

    @Unique
    private final Map<TileMultiblockMachineController, Integer> MMCEFlip$lastOrientation = new WeakHashMap<>();

    /**
     * Evicts the baked render task when the controller's orientation
     * changed, so the next frame bakes fresh buffers for the new rotation.
     * Runs every frame per visible controller through the buffer render
     * path.
     */
    @Inject(
            method = "renderWithBuffer(Lhellfirepvp/modularmachinery/common/tiles/base/TileMultiblockMachineController;)V",
            at = @At("HEAD"),
            remap = false
    )
    private void MMCEFlip$evictOnOrientationChange(TileMultiblockMachineController animatable, CallbackInfo ci) {
        if (!(animatable instanceof ControllerOrientationAccess) || animatable.getControllerRotation() == null) {
            return;
        }
        int current = Orientation.of(animatable.getControllerRotation(), ((ControllerOrientationAccess) animatable).MMCEFlip$getSpin()).getIndex();
        Integer last = MMCEFlip$lastOrientation.put(animatable, current);
        if (last != null && last != current) {
            GeoModelRenderTask removed = tasks.remove(animatable);
            if (removed != null) {
                // Releases the baked GL buffers; the task leaves the map so
                // the next frame bakes a fresh one with the new orientation.
                removed.reinitialize();
            }
        }
    }

    /**
     * Captures the tile behind the facing lookup during buffer baking.
     * Vanilla-scope orientations keep the real facing; everything else
     * returns {@code NORTH} so vanilla's horizontal-only rotation is a
     * no-op and the full orientation is applied right after it.
     */
    @Redirect(
            method = "render(Lhellfirepvp/modularmachinery/common/tiles/base/TileMultiblockMachineController;Lgithub/kasuminova/mmce/client/util/BufferProvider;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lhellfirepvp/modularmachinery/common/tiles/base/TileMultiblockMachineController;getControllerRotation()Lnet/minecraft/util/EnumFacing;"
            ),
            remap = false
    )
    private EnumFacing MMCEFlip$captureOrientation(TileMultiblockMachineController tile) {
        MMCEFlip_PENDING_ROTATION.remove();
        EnumFacing facing = tile.getControllerRotation();
        if (facing == null || !(tile instanceof ControllerOrientationAccess)) {
            return facing == null ? EnumFacing.NORTH : facing;
        }
        int spin = ((ControllerOrientationAccess) tile).MMCEFlip$getSpin();
        if (facing != EnumFacing.UP && facing != EnumFacing.DOWN && spin == 0) {
            return facing;
        }
        MMCEFlip_PENDING_ROTATION.set(Orientation.of(facing, spin));
        return EnumFacing.NORTH;
    }

    /**
     * Applies the captured orientation right after the (now neutralized)
     * vanilla rotation: the facing rotation, then the spin as a rotation
     * about the model's north axis.
     */
    @Inject(
            method = "render(Lhellfirepvp/modularmachinery/common/tiles/base/TileMultiblockMachineController;Lgithub/kasuminova/mmce/client/util/BufferProvider;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lgithub/kasuminova/mmce/client/renderer/MachineControllerRenderer;rotateBlockMatrix(Lnet/minecraft/util/EnumFacing;)V",
                    shift = At.Shift.AFTER
            ),
            remap = false
    )
    private static void MMCEFlip$applyOrientationRotation(CallbackInfo ci) {
        Orientation orientation = MMCEFlip_PENDING_ROTATION.get();
        MMCEFlip_PENDING_ROTATION.remove();
        if (orientation == null) {
            return;
        }
        MatrixStack stack = MATRIX_STACK.get();
        switch (orientation.getFacing()) {
            case UP:
                stack.rotateX(MMCEFlip_HALF_PI);
                break;
            case DOWN:
                stack.rotateX(-MMCEFlip_HALF_PI);
                break;
            case EAST:
                stack.rotateY(3F * MMCEFlip_HALF_PI);
                break;
            case SOUTH:
                stack.rotateY(MMCEFlip_PI);
                break;
            case WEST:
                stack.rotateY(MMCEFlip_HALF_PI);
                break;
            default:
                break;
        }
        if (orientation.getSpin() != 0) {
            stack.rotateZ(-orientation.getSpin() * MMCEFlip_HALF_PI);
        }
    }
}
