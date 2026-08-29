package github.dipnchips.mmceflip.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import hellfirepvp.modularmachinery.common.crafting.helper.ComponentSelectorTag;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.TaggedPositionBlockArray;
import hellfirepvp.modularmachinery.common.modifier.SingleBlockModifierReplacement;
import hellfirepvp.modularmachinery.common.util.BlockArray;
import hellfirepvp.modularmachinery.common.util.BlockArrayCache;
import hellfirepvp.modularmachinery.common.util.IBlockStateDescriptor;

import github.dipnchips.mmceflip.mixin.BlockInformationAccessor;

/**
 * Builds and caches rotated copies of Modular Machinery pattern structures
 * for every one of the 24 cube orientations.
 *
 * Entries are keyed by the source structure's {@link BlockArray#uid} and the
 * orientation index, and are prebuilt when Modular Machinery builds its own
 * structure cache. Every rotated {@link BlockArray.BlockInformation} keeps
 * its matching tag, preview tag and NBT checker so pattern matching behaves
 * identically to the entries Modular Machinery builds for horizontal
 * facings.
 *
 * Spin-zero entries for the up and down facings are additionally mirrored
 * into {@code BlockArrayCache} itself, so third-party tools that look
 * patterns up by facing alone (the assembly stick, addon machine builders)
 * keep working against vertically tipped controllers.
 */
public final class OrientationPatterns {

    private static final Map<Long, TaggedPositionBlockArray[]> PATTERN_CACHE = new ConcurrentHashMap<>();
    private static final Map<Long, BlockArray[]> BLOCK_ARRAY_CACHE = new ConcurrentHashMap<>();

    private OrientationPatterns() {
    }

    /**
     * Prebuilds all 24 orientation entries for a machine's pattern and its
     * multi-block modifier replacements, and mirrors the vertical spin-zero
     * entries into Modular Machinery's own facing-keyed cache.
     *
     * @return the number of cache entries created.
     */
    public static int prebuildMachine(DynamicMachine machine) {
        int count = 0;
        count += prebuildPattern(machine.getPattern());
        for (hellfirepvp.modularmachinery.common.modifier.MultiBlockModifierReplacement replacement : machine.getMultiBlockModifiers()) {
            count += prebuildBlockArray(replacement.getBlockArray());
        }
        return count;
    }

    public static int prebuildPattern(TaggedPositionBlockArray source) {
        TaggedPositionBlockArray[] entries = PATTERN_CACHE.computeIfAbsent(source.uid, key -> new TaggedPositionBlockArray[Orientation.COUNT]);
        int count = 0;
        for (int i = 0; i < Orientation.COUNT; i++) {
            Orientation orientation = Orientation.byIndex(i);
            if (entries[i] == null) {
                entries[i] = rotatePattern(source, orientation);
                count++;
            }
            // Mirror vertical spin-zero entries into the facing-keyed cache
            // so facing-only consumers (assembly stick, addon builders) can
            // reach them. Horizontal entries already exist there.
            if (orientation.getSpin() == 0 && isVertical(orientation)) {
                BlockArrayCache.addBlockArrayCache(entries[i], orientation.getFacing());
            }
        }
        return count;
    }

    public static int prebuildBlockArray(BlockArray source) {
        BlockArray[] entries = BLOCK_ARRAY_CACHE.computeIfAbsent(source.uid, key -> new BlockArray[Orientation.COUNT]);
        int count = 0;
        for (int i = 0; i < Orientation.COUNT; i++) {
            if (entries[i] == null) {
                entries[i] = rotateBlockArray(source, Orientation.byIndex(i));
                count++;
            }
        }
        return count;
    }

    /**
     * Fetches (building on demand if a machine was never prebuilt) the
     * rotated form of a tagged machine pattern.
     */
    public static TaggedPositionBlockArray getPattern(TaggedPositionBlockArray source, Orientation orientation) {
        if (orientation.isIdentity()) {
            return source;
        }
        TaggedPositionBlockArray[] entries = PATTERN_CACHE.computeIfAbsent(source.uid, key -> new TaggedPositionBlockArray[Orientation.COUNT]);
        TaggedPositionBlockArray rotated = entries[orientation.getIndex()];
        if (rotated == null) {
            rotated = rotatePattern(source, orientation);
            entries[orientation.getIndex()] = rotated;
        }
        return rotated;
    }

    /**
     * Fetches (building on demand) the rotated form of a plain block array.
     */
    public static BlockArray getBlockArray(BlockArray source, Orientation orientation) {
        if (orientation.isIdentity()) {
            return source;
        }
        BlockArray[] entries = BLOCK_ARRAY_CACHE.computeIfAbsent(source.uid, key -> new BlockArray[Orientation.COUNT]);
        BlockArray rotated = entries[orientation.getIndex()];
        if (rotated == null) {
            rotated = rotateBlockArray(source, orientation);
            entries[orientation.getIndex()] = rotated;
        }
        return rotated;
    }

    public static boolean isVertical(Orientation orientation) {
        EnumFacing facing = orientation.getFacing();
        return facing == EnumFacing.UP || facing == EnumFacing.DOWN;
    }

    /**
     * Rotates a full tagged machine pattern (positions, block information and
     * selector tags) under the given orientation.
     */
    public static TaggedPositionBlockArray rotatePattern(TaggedPositionBlockArray source, Orientation orientation) {
        if (orientation.isIdentity()) {
            return source;
        }
        TaggedPositionBlockArray out = new TaggedPositionBlockArray(source.uid);
        for (Map.Entry<BlockPos, BlockArray.BlockInformation> entry : source.getPattern().entrySet()) {
            out.addBlock(orientation.apply(entry.getKey()), rotateInfo(entry.getValue(), orientation));
        }
        for (Map.Entry<BlockPos, ComponentSelectorTag> entry : source.getTaggedPositions().entrySet()) {
            out.setTag(orientation.apply(entry.getKey()), entry.getValue());
        }
        // Component binding (hatches, buses, casings) reads the tile blocks
        // array, which is only populated by this cache flush.
        out.flushTileBlocksCache();
        return out;
    }

    /**
     * Rotates a plain block array (used for multi-block modifier replacement
     * structures) under the given orientation.
     */
    public static BlockArray rotateBlockArray(BlockArray source, Orientation orientation) {
        if (orientation.isIdentity()) {
            return source;
        }
        BlockArray out = new BlockArray(source.uid);
        for (Map.Entry<BlockPos, BlockArray.BlockInformation> entry : source.getPattern().entrySet()) {
            out.addBlock(orientation.apply(entry.getKey()), rotateInfo(entry.getValue(), orientation));
        }
        out.flushTileBlocksCache();
        return out;
    }

    /**
     * Rotates the single-block modifier map of a machine under the given
     * orientation, mirroring what repeated {@code rotateYCCW()} does for
     * horizontal facings.
     */
    public static DynamicMachine.ModifierReplacementMap rotateReplacements(DynamicMachine.ModifierReplacementMap source, Orientation orientation) {
        if (orientation.isIdentity()) {
            return source;
        }
        DynamicMachine.ModifierReplacementMap out = new DynamicMachine.ModifierReplacementMap();
        for (Map.Entry<BlockPos, List<BlockArray.BlockInformation>> entry : source.entrySet()) {
            BlockPos at = orientation.apply(entry.getKey());
            List<BlockArray.BlockInformation> rotated = new ArrayList<>(entry.getValue().size());
            for (BlockArray.BlockInformation info : entry.getValue()) {
                rotated.add(rotateInfo(info, orientation));
            }
            out.put(at, rotated);
        }
        return out;
    }

    /**
     * Produces a rotated copy of one block information entry.
     *
     * Descriptors with multiple applicable states are exploded into
     * single-state descriptors; matching semantics are unchanged because the
     * pattern accepts a position when any descriptor matches.
     */
    public static BlockArray.BlockInformation rotateInfo(BlockArray.BlockInformation source, Orientation orientation) {
        if (orientation.isIdentity()) {
            return source;
        }
        BlockInformationAccessor access = (BlockInformationAccessor) (Object) source;

        List<IBlockStateDescriptor> rotated = new ArrayList<>();
        for (IBlockStateDescriptor descriptor : access.MMCEFlip$getMatchingStates()) {
            for (IBlockState state : descriptor.getApplicable()) {
                rotated.add(new IBlockStateDescriptor(StateRotator.rotate(state, orientation)).canonicalize());
            }
        }

        BlockArray.BlockInformation copy = new BlockArray.BlockInformation(rotated);
        copy.setMatchingTag(source.getMatchingTag());
        copy.setPreviewTag(source.getPreviewTag());
        ((BlockInformationAccessor) (Object) copy).MMCEFlip$setNbtChecker(access.MMCEFlip$getNbtChecker());
        return copy.canonicalize();
    }

    /**
     * Rotates a single-block modifier replacement's block information
     * (convenience wrapper used during structure re-checks).
     */
    public static BlockArray.BlockInformation rotateReplacement(SingleBlockModifierReplacement replacement, Orientation orientation) {
        return rotateInfo(replacement.getBlockInformation(), orientation);
    }
}
