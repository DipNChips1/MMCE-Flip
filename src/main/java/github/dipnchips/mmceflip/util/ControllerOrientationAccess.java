package github.dipnchips.mmceflip.util;

/**
 * Plain interface implemented by the controller tile mixin, so ordinary code
 * (like the block's click handling) can read and write the controller's spin
 * without referencing mixin classes.
 */
public interface ControllerOrientationAccess {

    /**
     * @return the current spin around the controller's facing axis (0-3).
     */
    int MMCEFlip$getSpin();

    /**
     * Sets the spin around the controller's facing axis (0-3).
     */
    void MMCEFlip$setSpin(int spin);

    /**
     * Called after the controller's orientation (facing or spin) changed
     * in-place. Resets any formed structure so the next structure check
     * re-discovers the machine for the new orientation, mirroring what
     * breaking and replacing the controller would do.
     */
    void MMCEFlip$onOrientationChanged();
}
