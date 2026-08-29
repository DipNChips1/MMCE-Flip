package github.dipnchips.mmceflip.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/**
 * The 24 rotations of a cube, addressed as (facing, spin).
 *
 * An orientation describes how a canonical machine pattern (stored facing
 * north, body extending behind the controller) is tipped and turned in the
 * world: {@code facing} is the direction the controller's face points, and
 * {@code spin} rotates the pattern around that facing axis in 90 degree steps.
 *
 * The class is built on integer 3x3 rotation matrices. The matrix core is
 * deliberately free of Minecraft types so the rotation group can be verified
 * exhaustively by unit tests; the BlockPos and EnumFacing wrappers are thin.
 *
 * Convention: transformations map pattern space to world space, so
 * {@link #apply(BlockPos)} converts a canonical pattern offset into a world
 * offset, and {@link #undo(BlockPos)} converts it back.
 */
public final class Orientation {

    /** Number of distinct orientations of a cube. */
    public static final int COUNT = 24;

    // EnumFacing ordinals, repeated here so the pure-math core stays testable.
    // Order must match net.minecraft.util.EnumFacing.values():
    // DOWN, UP, NORTH, SOUTH, WEST, EAST.
    public static final int DOWN = 0;
    public static final int UP = 1;
    public static final int NORTH = 2;
    public static final int SOUTH = 3;
    public static final int WEST = 4;
    public static final int EAST = 5;

    private static final int[][][] FACING_MATRICES = {
            /* DOWN  */ {{1, 0, 0}, {0, 0, 1}, {0, -1, 0}},
            /* UP    */ {{1, 0, 0}, {0, 0, -1}, {0, 1, 0}},
            /* NORTH */ {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}},
            /* SOUTH */ {{-1, 0, 0}, {0, 1, 0}, {0, 0, -1}},
            /* WEST  */ {{0, 0, 1}, {0, 1, 0}, {-1, 0, 0}},
            /* EAST  */ {{0, 0, -1}, {0, 1, 0}, {1, 0, 0}},
    };

    private static final int[][][] SPIN_MATRICES = {
            /* DOWN  */ {{0, 0, -1}, {0, 1, 0}, {1, 0, 0}},
            /* UP    */ {{0, 0, 1}, {0, 1, 0}, {-1, 0, 0}},
            /* NORTH */ {{0, 1, 0}, {-1, 0, 0}, {0, 0, 1}},
            /* SOUTH */ {{0, -1, 0}, {1, 0, 0}, {0, 0, 1}},
            /* WEST  */ {{1, 0, 0}, {0, 0, 1}, {0, -1, 0}},
            /* EAST  */ {{1, 0, 0}, {0, 0, -1}, {0, 1, 0}},
    };

    /** Index of the identity orientation (facing north, no spin). */
    public static final int IDENTITY_INDEX = NORTH * 4;

    private static final Orientation[] BY_INDEX = new Orientation[COUNT];

    static {
        int index = 0;
        for (int facing = 0; facing < 6; facing++) {
            for (int spin = 0; spin < 4; spin++) {
                BY_INDEX[index++] = new Orientation(facing, spin);
            }
        }
    }

    private final int facing;
    private final int spin;
    private final int index;
    private final int[][] matrix;
    private final int[][] inverse;

    private Orientation(int facing, int spin) {
        this.facing = facing;
        this.spin = spin;
        this.index = facing * 4 + spin;

        int[][] f = FACING_MATRICES[facing];
        int[][] s = SPIN_MATRICES[facing];
        int[][] r = f;
        for (int i = 0; i < spin; i++) {
            r = mul(s, r);
        }
        this.matrix = r;
        this.inverse = transpose(r);
    }

    /**
     * @param facing the direction the controller's face points.
     * @param spin   rotation about the facing axis in 90 degree steps (0-3).
     */
    public static Orientation of(EnumFacing facing, int spin) {
        return byIndex(facing.ordinal(), ((spin % 4) + 4) % 4);
    }

    public static Orientation byIndex(int facing, int spin) {
        return BY_INDEX[facing * 4 + (((spin % 4) + 4) % 4)];
    }

    /**
     * @return the orientation with index {@code index} (0-23); indices are
     * {@code facingOrdinal * 4 + spin}, so the identity (facing north, no
     * spin) has index {@link #IDENTITY_INDEX}.
     */
    public static Orientation byIndex(int index) {
        return BY_INDEX[index];
    }

    public static Orientation identity() {
        return BY_INDEX[IDENTITY_INDEX];
    }

    public int getIndex() {
        return index;
    }

    public EnumFacing getFacing() {
        return EnumFacing.values()[facing];
    }

    public int getSpin() {
        return spin;
    }

    public boolean isIdentity() {
        return index == IDENTITY_INDEX;
    }

    // --- Pure matrix core -------------------------------------------------

    /**
     * Applies the rotation to a point (pattern space to world space).
     */
    public int[] apply(int x, int y, int z) {
        return new int[] {
                matrix[0][0] * x + matrix[0][1] * y + matrix[0][2] * z,
                matrix[1][0] * x + matrix[1][1] * y + matrix[1][2] * z,
                matrix[2][0] * x + matrix[2][1] * y + matrix[2][2] * z
        };
    }

    /**
     * Undoes {@link #apply(int, int, int)} (world space to pattern space).
     */
    public int[] undo(int x, int y, int z) {
        return new int[] {
                inverse[0][0] * x + inverse[0][1] * y + inverse[0][2] * z,
                inverse[1][0] * x + inverse[1][1] * y + inverse[1][2] * z,
                inverse[2][0] * x + inverse[2][1] * y + inverse[2][2] * z
        };
    }

    /**
     * Remaps a direction's unit vector through this rotation. Accepts and
     * returns unit vectors as (dx, dy, dz).
     */
    public int[] applyDirection(int dx, int dy, int dz) {
        return apply(dx, dy, dz);
    }

    /**
     * Remaps a log-style axis name ("x", "y", "z") through this rotation.
     * Axes are unoriented, so the result is normalized to the positive axis.
     */
    public String remapLogAxis(String axis) {
        int[] v;
        switch (axis) {
            case "x": v = new int[] {1, 0, 0}; break;
            case "y": v = new int[] {0, 1, 0}; break;
            case "z": v = new int[] {0, 0, 1}; break;
            default: return axis;
        }
        int[] r = apply(v[0], v[1], v[2]);
        if (r[0] != 0) return "x";
        if (r[1] != 0) return "y";
        return "z";
    }

    /**
     * Composes this rotation with {@code after} (this applied first).
     */
    public Orientation compose(Orientation after) {
        int[][] m = mul(after.matrix, this.matrix);
        return findMatching(m);
    }

    /**
     * @return the inverse rotation.
     */
    public Orientation invert() {
        return findMatching(inverse);
    }

    public int[][] getMatrix() {
        return matrix;
    }

    private static Orientation findMatching(int[][] m) {
        for (Orientation o : BY_INDEX) {
            if (java.util.Arrays.deepEquals(o.matrix, m)) {
                return o;
            }
        }
        throw new IllegalStateException("Result is not a cube rotation");
    }

    private static int[][] mul(int[][] a, int[][] b) {
        int[][] out = new int[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                int sum = 0;
                for (int k = 0; k < 3; k++) {
                    sum += a[i][k] * b[k][j];
                }
                out[i][j] = sum;
            }
        }
        return out;
    }

    private static int[][] transpose(int[][] m) {
        int[][] out = new int[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                out[i][j] = m[j][i];
            }
        }
        return out;
    }

    // --- Minecraft wrappers ------------------------------------------------

    public BlockPos apply(BlockPos pos) {
        int[] r = apply(pos.getX(), pos.getY(), pos.getZ());
        return new BlockPos(r[0], r[1], r[2]);
    }

    public BlockPos undo(BlockPos pos) {
        int[] r = undo(pos.getX(), pos.getY(), pos.getZ());
        return new BlockPos(r[0], r[1], r[2]);
    }

    public EnumFacing rotateFacing(EnumFacing dir) {
        int[] v = directionVector(dir);
        int[] r = apply(v[0], v[1], v[2]);
        return facingFromVector(r[0], r[1], r[2]);
    }

    private static int[] directionVector(EnumFacing dir) {
        switch (dir) {
            case DOWN: return new int[] {0, -1, 0};
            case UP: return new int[] {0, 1, 0};
            case NORTH: return new int[] {0, 0, -1};
            case SOUTH: return new int[] {0, 0, 1};
            case WEST: return new int[] {-1, 0, 0};
            case EAST: return new int[] {1, 0, 0};
            default: throw new IllegalArgumentException(dir.name());
        }
    }

    private static EnumFacing facingFromVector(int x, int y, int z) {
        if (x > 0) return EnumFacing.EAST;
        if (x < 0) return EnumFacing.WEST;
        if (y > 0) return EnumFacing.UP;
        if (y < 0) return EnumFacing.DOWN;
        if (z > 0) return EnumFacing.SOUTH;
        return EnumFacing.NORTH;
    }

    /**
     * Helper for tests and diagnostics: lists every orientation as
     * "facing/spin". Not used at runtime.
     */
    public static List<String> describeAll() {
        List<String> out = new ArrayList<>(COUNT);
        for (Orientation o : BY_INDEX) {
            out.add(o.getFacing().getName() + "/" + o.getSpin());
        }
        return out;
    }
}
