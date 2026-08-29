package github.dipnchips.mmceflip.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.tiles.base.TileMultiblockMachineController;

import github.dipnchips.mmceflip.util.ControllerOrientationAccess;
import github.dipnchips.mmceflip.util.ControllerSpinProperty;

/**
 * Opens the machine controller block up to all six facings.
 *
 * The facing property loses its horizontal-only restriction, the metadata
 * encoding gains {@code 4 = up} and {@code 5 = down} (existing worlds with
 * horizontal metadata 0-3 are untouched), and sneaking while right-clicking
 * the controller with an empty hand cycles it through the six facings
 * instead of opening the GUI.
 *
 * Spin cycling while holding a stick is handled by {@code ControllerSpinHandler}
 * through {@code PlayerInteractEvent.RightClickBlock}, because vanilla skips
 * {@code onBlockActivated} while sneaking with an item in hand.
 *
 * The state container is upgraded to an {@code ExtendedBlockState} and
 * {@code getExtendedState} attaches the tile's spin as an unlisted property,
 * so baked models can render the block model at the full orientation.
 */
@Mixin(BlockController.class)
public abstract class MixinBlockController {

    private static final EnumFacing[] MMCEFlip_CYCLE = {
            EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST,
            EnumFacing.UP, EnumFacing.DOWN
    };

    /**
     * Rebuilds the facing property without the horizontal-only filter, so
     * {@link BlockController#FACING} accepts up and down as well.
     */
    @Redirect(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/properties/PropertyEnum;create(Ljava/lang/String;Ljava/lang/Class;[Ljava/lang/Enum;)Lnet/minecraft/block/properties/PropertyEnum;"
            )
    )
    private static PropertyEnum<EnumFacing> MMCEFlip$unrestrictFacing(String name, Class<EnumFacing> valueClass, Enum<?>[] allowedValues) {
        return PropertyEnum.create(name, valueClass);
    }

    /**
     * Metadata encoding: horizontals keep their vanilla index, verticals use
     * the free slots 4 and 5.
     */
    @Redirect(
            method = "getMetaFromState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/EnumFacing;getHorizontalIndex()I")
    )
    private int MMCEFlip$metaForFacing(EnumFacing facing) {
        if (facing == EnumFacing.UP) {
            return 4;
        }
        if (facing == EnumFacing.DOWN) {
            return 5;
        }
        return facing.getHorizontalIndex();
    }

    @Redirect(
            method = "getStateFromMeta",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/EnumFacing;byHorizontalIndex(I)Lnet/minecraft/util/EnumFacing;")
    )
    private EnumFacing MMCEFlip$facingFromMeta(int meta) {
        if (meta == 4) {
            return EnumFacing.UP;
        }
        if (meta == 5) {
            return EnumFacing.DOWN;
        }
        return EnumFacing.byHorizontalIndex(meta);
    }

    /**
     * Sneak-right-click with an empty hand cycles the controller through the
     * six facings instead of opening the GUI. The formed structure is reset
     * so it re-forms (or correctly reports missing structure) for the new
     * facing on the next structure check.
     */
    @Inject(method = "onBlockActivated", at = @At("HEAD"), cancellable = true)
    private void MMCEFlip$cycleFacing(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn,
                                         EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (!playerIn.isSneaking()) {
            return;
        }
        if (worldIn.isRemote) {
            cir.setReturnValue(true);
            return;
        }

        TileEntity te = worldIn.getTileEntity(pos);

        EnumFacing current = state.getValue(BlockController.FACING);
        EnumFacing next = MMCEFlip_CYCLE[(facingIndex(current) + 1) % MMCEFlip_CYCLE.length];
        worldIn.setBlockState(pos, state.withProperty(BlockController.FACING, next), 3);

        if (te instanceof ControllerOrientationAccess) {
            ((ControllerOrientationAccess) te).MMCEFlip$onOrientationChanged();
        }

        playerIn.sendStatusMessage(new TextComponentTranslation("message.mmceflip.rotation", next.getName()), true);
        cir.setReturnValue(true);
    }

    private static int facingIndex(EnumFacing facing) {
        for (int i = 0; i < MMCEFlip_CYCLE.length; i++) {
            if (MMCEFlip_CYCLE[i] == facing) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Builds the controller's state container as an {@code ExtendedBlockState}
     * carrying the spin as an unlisted property. For all listed-property
     * behavior (metadata, blockstate files, matching) this is identical to
     * the vanilla container; the unlisted property only becomes visible to
     * baked models through {@link #getExtendedState}.
     */
    @Overwrite
    protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(
                (Block) (Object) this,
                new IProperty[] { BlockController.FACING, BlockController.FORMED },
                new IUnlistedProperty[] { ControllerSpinProperty.SPIN });
    }

    /**
     * Attaches the tile's spin to the extended state during chunk rendering,
     * so a spin-aware baked model can rotate the controller's block model.
     * Called on the client render path only.
     */
    public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (state instanceof IExtendedBlockState) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof ControllerOrientationAccess) {
                return ((IExtendedBlockState) state)
                        .withProperty(ControllerSpinProperty.SPIN, ((ControllerOrientationAccess) te).MMCEFlip$getSpin());
            }
        }
        return state;
    }
}
