package github.dipnchips.mmceflip.mixin;

import java.util.List;

import github.kasuminova.mmce.common.helper.AdvancedBlockChecker;
import hellfirepvp.modularmachinery.common.util.BlockArray;
import hellfirepvp.modularmachinery.common.util.IBlockStateDescriptor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read access to {@link BlockArray.BlockInformation} internals that have no
 * public getter, plus write access to the NBT checker, so vertically rotated
 * copies can preserve every matching property of the original.
 */
@Mixin(BlockArray.BlockInformation.class)
public interface BlockInformationAccessor {

    @Accessor("matchingStates")
    List<IBlockStateDescriptor> MMCEFlip$getMatchingStates();

    @Accessor("nbtChecker")
    AdvancedBlockChecker MMCEFlip$getNbtChecker();

    @Accessor("nbtChecker")
    void MMCEFlip$setNbtChecker(AdvancedBlockChecker checker);
}
