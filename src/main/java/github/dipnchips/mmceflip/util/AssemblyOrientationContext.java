package github.dipnchips.mmceflip.util;

import javax.annotation.Nullable;

import hellfirepvp.modularmachinery.common.machine.TaggedPositionBlockArray;

/**
 * Hand-off slot between the assembly event handler mixin and its cache
 * lookup redirect, so the assembly stick can build the structure for the
 * controller's full orientation (facing plus spin) instead of only the
 * facing-keyed spin-zero variant.
 *
 * The orientation itself is carried alongside the pattern so downstream
 * redirects can remap positions with the same full transform, instead of
 * the horizontal-only rotation the original code applies.
 *
 * A plain class because mixin classes cannot be referenced from normal
 * code at runtime; the slot is set for the duration of one assembly
 * preparation and cleared afterwards.
 */
public final class AssemblyOrientationContext {

    @Nullable
    private static TaggedPositionBlockArray overridePattern;

    @Nullable
    private static Orientation overrideOrientation;

    private AssemblyOrientationContext() {
    }

    public static void setOverride(TaggedPositionBlockArray pattern, Orientation orientation) {
        overridePattern = pattern;
        overrideOrientation = orientation;
    }

    public static boolean hasOverride() {
        return overridePattern != null;
    }

    public static TaggedPositionBlockArray getOverride() {
        return overridePattern;
    }

    @Nullable
    public static Orientation getOrientation() {
        return overrideOrientation;
    }

    public static void clear() {
        overridePattern = null;
        overrideOrientation = null;
    }
}
