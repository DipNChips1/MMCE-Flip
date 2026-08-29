package github.dipnchips.mmceflip.util;

import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;

import java.util.Collection;

/**
 * Best-effort rotation of block states under a full cube orientation.
 *
 * Vanilla block states can only represent a subset of all 24 tipped and
 * turned orientations: for example a furnace cannot face up, and a stair
 * cannot lie on its side. When a state cannot express its rotated form, the
 * original state is kept, so pattern matching stays lenient instead of
 * rejecting the machine outright. This mainly affects decorative directional
 * blocks; Modular Machinery's own casings, buses and hatches are
 * rotation-invariant.
 */
public final class StateRotator {

    private StateRotator() {
    }

    /**
     * Rotates {@code state} so its directional properties follow the given
     * orientation. The identity orientation returns the state unchanged.
     */
    public static IBlockState rotate(IBlockState state, Orientation orientation) {
        if (orientation.isIdentity()) {
            return state;
        }
        for (IProperty<?> property : state.getPropertyKeys()) {
            state = rotateProperty(state, property, orientation);
        }
        return state;
    }

    private static <T extends Comparable<T>> IBlockState rotateProperty(IBlockState state, IProperty<T> property, Orientation orientation) {
        if (property instanceof PropertyDirection) {
            return rotateDirectionProperty(state, (PropertyDirection) property, orientation);
        }
        if (isLogAxisProperty(property)) {
            return rotateLogAxisProperty(state, property, orientation);
        }
        return state;
    }

    private static IBlockState rotateDirectionProperty(IBlockState state, PropertyDirection property, Orientation orientation) {
        EnumFacing current = state.getValue(property);
        EnumFacing rotated = orientation.rotateFacing(current);
        if (rotated == current) {
            return state;
        }
        // Blocks restricted to a subset of facings (for example horizontal
        // only) cannot represent the rotated direction; keep the original
        // value so the state remains valid.
        if (!property.getAllowedValues().contains(rotated)) {
            return state;
        }
        return state.withProperty(property, rotated);
    }

    private static boolean isLogAxisProperty(IProperty<?> property) {
        if (!(property instanceof PropertyEnum)) {
            return false;
        }
        Collection<?> allowed = property.getAllowedValues();
        if (allowed.size() != 3) {
            return false;
        }
        return "axis".equals(property.getName())
                && containsName(allowed, "x") && containsName(allowed, "y") && containsName(allowed, "z");
    }

    private static boolean containsName(Collection<?> values, String name) {
        for (Object value : values) {
            if (value instanceof Enum) {
                if (name.equals(((Enum<?>) value).name().toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static IBlockState rotateLogAxisProperty(IBlockState state, IProperty property, Orientation orientation) {
        Comparable currentValue = state.getValue(property);
        String remapped = orientation.remapLogAxis(currentValue.toString());
        com.google.common.base.Optional<?> newValue = property.parseValue(remapped);
        if (newValue.isPresent()) {
            return state.withProperty(property, (Comparable) newValue.get());
        }
        return state;
    }
}
