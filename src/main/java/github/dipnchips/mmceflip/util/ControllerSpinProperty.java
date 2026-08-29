package github.dipnchips.mmceflip.util;

import net.minecraftforge.common.property.IUnlistedProperty;

/**
 * Unlisted block state property carrying the controller's spin (0-3) into
 * the model rendering pipeline.
 *
 * Unlisted properties never touch metadata or serialization; they exist
 * only in the transient extended state handed to baked models during
 * chunk rendering, which is exactly where the spin is needed.
 */
public final class ControllerSpinProperty implements IUnlistedProperty<Integer> {

    public static final ControllerSpinProperty SPIN = new ControllerSpinProperty();

    private ControllerSpinProperty() {
    }

    @Override
    public String getName() {
        return "mmceflip_spin";
    }

    @Override
    public boolean isValid(Integer value) {
        return value != null && value >= 0 && value <= 3;
    }

    @Override
    public Class<Integer> getType() {
        return Integer.class;
    }

    @Override
    public String valueToString(Integer value) {
        return String.valueOf(value);
    }
}
