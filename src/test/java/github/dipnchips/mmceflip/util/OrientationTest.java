package github.dipnchips.mmceflip.util;

import org.junit.jupiter.api.Test;

import net.minecraft.util.EnumFacing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link Orientation} really is the rotation group of the cube:
 * 24 distinct elements, closed under composition, each with an inverse inside
 * the group, plus orthonormality and the concrete behaviors the rest of the
 * mod relies on.
 */
class OrientationTest {

    private static List<Orientation> all() {
        List<Orientation> out = new ArrayList<>(Orientation.COUNT);
        for (int i = 0; i < Orientation.COUNT; i++) {
            out.add(Orientation.byIndex(i));
        }
        return out;
    }

    @Test
    void exactlyTwentyFourDistinctOrientations() {
        Set<String> distinct = new HashSet<>();
        for (Orientation o : all()) {
            distinct.add(matrixKey(o));
        }
        assertEquals(Orientation.COUNT, distinct.size(), "all 24 rotation matrices must be distinct");
        assertEquals(Orientation.COUNT, all().size());
    }

    @Test
    void closedUnderComposition() {
        for (Orientation a : all()) {
            for (Orientation b : all()) {
                Orientation composed = a.compose(b);
                boolean found = false;
                for (Orientation member : all()) {
                    if (matrixKey(member).equals(matrixKey(composed))) {
                        found = true;
                        break;
                    }
                }
                assertTrue(found, "composition must stay in the group: " + matrixKey(a) + " then " + matrixKey(b));
            }
        }
    }

    @Test
    void everyElementHasInverseInTheGroup() {
        for (Orientation o : all()) {
            Orientation inverse = o.invert();
            // o then inverse must be the identity matrix.
            int[][] product = multiply(o.getMatrix(), inverse.getMatrix());
            assertArrayEquals(new int[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}, product,
                    "orientation times its inverse must be identity: " + matrixKey(o));
        }
    }

    @Test
    void identityIsNeutral() {
        Orientation identity = Orientation.identity();
        for (Orientation o : all()) {
            assertArrayEquals(o.getMatrix(), multiply(identity.getMatrix(), o.getMatrix()));
            assertArrayEquals(o.getMatrix(), multiply(o.getMatrix(), identity.getMatrix()));
        }
    }

    @Test
    void matricesAreOrthonormalIntegerRotations() {
        for (Orientation o : all()) {
            int[][] m = o.getMatrix();
            // Each row must be a signed unit vector.
            for (int i = 0; i < 3; i++) {
                int norm = m[i][0] * m[i][0] + m[i][1] * m[i][1] + m[i][2] * m[i][2];
                assertEquals(1, norm, "row " + i + " of " + matrixKey(o) + " must be a unit vector");
            }
            // Rows must be mutually orthogonal.
            for (int i = 0; i < 3; i++) {
                for (int j = i + 1; j < 3; j++) {
                    int dot = m[i][0] * m[j][0] + m[i][1] * m[j][1] + m[i][2] * m[j][2];
                    assertEquals(0, dot, "rows " + i + "," + j + " of " + matrixKey(o) + " must be orthogonal");
                }
            }
            // Proper rotation (no reflection): determinant +1.
            int det = m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1])
                    - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
                    + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
            assertEquals(1, det, "determinant must be +1 for " + matrixKey(o));
        }
    }

    @Test
    void fourWorldSpinsAboutAnyFaceAreIdentity() {
        // The world-axis spin S_f for a facing is R(f,1) after R(f,0):
        // S_f = R(f,1) . R(f,0)^-1. Composing S_f with itself four times must
        // return to the identity, and stepping the spin parameter must equal
        // applying S_f to the previous orientation.
        for (int facing = 0; facing < 6; facing++) {
            Orientation r0 = Orientation.byIndex(facing, 0);
            Orientation r1 = Orientation.byIndex(facing, 1);
            Orientation spin = r0.invert().compose(r1); // S_f = r1 . r0^-1

            Orientation four = spin.compose(spin).compose(spin).compose(spin);
            assertArrayEquals(new int[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}, four.getMatrix(),
                    "four world spins about facing index " + facing + " must be identity");

            for (int s = 0; s < 4; s++) {
                Orientation stepped = Orientation.byIndex(facing, s);
                Orientation next = Orientation.byIndex(facing, (s + 1) % 4);
                // stepped then spin == next  (next = S_f . stepped)
                assertArrayEquals(next.getMatrix(), multiply(spin.getMatrix(), stepped.getMatrix()),
                        "spin step must equal the world spin for facing index " + facing + " spin " + s);
            }
        }
    }

    @Test
    void everyFacingIsReachableAsNorth() {
        // The controller's face direction is canonical -Z (north); every
        // orientation must map it to its declared facing. Order matches
        // EnumFacing.values(): DOWN, UP, NORTH, SOUTH, WEST, EAST.
        int[][] directionsWithOrdinals = {
                {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
        };
        for (Orientation o : all()) {
            assertArrayEquals(directionsWithOrdinals[o.getFacing().ordinal()],
                    o.apply(0, 0, -1),
                    "north must map to the declared facing for " + matrixKey(o));
        }
    }

    @Test
    void applyThenUndoIsIdentity() {
        int[][] samples = {
                {0, 0, 0}, {3, -2, 5}, {-7, 1, 4}, {0, 10, 0}, {2, 2, 2}, {-1, -1, -1}, {6, 0, -9}
        };
        for (Orientation o : all()) {
            for (int[] p : samples) {
                int[] applied = o.apply(p[0], p[1], p[2]);
                int[] roundTrip = o.undo(applied[0], applied[1], applied[2]);
                assertArrayEquals(p, roundTrip, o + " apply then undo must be identity");
            }
        }
    }

    @Test
    void pitchUpRegression() {
        // Facing up with no spin was the first vertical transform shipped;
        // its behavior is pinned here so the generalization cannot drift.
        Orientation pitchUp = Orientation.byIndex(Orientation.UP, 0);
        assertArrayEquals(new int[] {0, 1, 0}, pitchUp.apply(0, 0, -1)); // north -> up
        assertArrayEquals(new int[] {0, 0, 1}, pitchUp.apply(0, 1, 0));  // up -> south
        assertArrayEquals(new int[] {0, -1, 0}, pitchUp.apply(0, 0, 1)); // south -> down
        assertArrayEquals(new int[] {0, 0, -1}, pitchUp.apply(0, -1, 0)); // down -> north
        assertArrayEquals(new int[] {1, 0, 0}, pitchUp.apply(1, 0, 0));  // east fixed
        assertArrayEquals(new int[] {-1, 0, 0}, pitchUp.apply(-1, 0, 0)); // west fixed
    }

    @Test
    void pitchDownRegression() {
        Orientation pitchDown = Orientation.byIndex(Orientation.DOWN, 0);
        assertArrayEquals(new int[] {0, -1, 0}, pitchDown.apply(0, 0, -1)); // north -> down
        assertArrayEquals(new int[] {0, 0, 1}, pitchDown.apply(0, -1, 0));  // down -> south
        assertArrayEquals(new int[] {0, 1, 0}, pitchDown.apply(0, 0, 1));   // south -> up
        assertArrayEquals(new int[] {0, 0, -1}, pitchDown.apply(0, 1, 0));  // up -> north
    }

    @Test
    void pitchUpAndDownAreInverse() {
        Orientation up = Orientation.byIndex(Orientation.UP, 0);
        Orientation down = Orientation.byIndex(Orientation.DOWN, 0);
        int[][] identity = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        assertArrayEquals(identity, (up.compose(down)).getMatrix());
        assertArrayEquals(identity, (down.compose(up)).getMatrix());
    }

    @Test
    void worldSpinKeepsFacingAxisFixed() {
        // The world-axis spin S_f (see fourWorldSpinsAboutAnyFaceAreIdentity)
        // must fix the facing axis; the composite orientation only promises
        // north -> facing, which everyFacingIsReachableAsNorth covers.
        for (int facing = 0; facing < 6; facing++) {
            Orientation r0 = Orientation.byIndex(facing, 0);
            Orientation r1 = Orientation.byIndex(facing, 1);
            Orientation spin = r0.invert().compose(r1);

            EnumFacing axis = r0.getFacing();
            int[] v = directionOf(axis);
            int[] r = spin.apply(v[0], v[1], v[2]);
            assertArrayEquals(v, r, "world spin must keep the facing axis fixed: " + matrixKey(spin));
        }
    }

    @Test
    void logAxisRemap() {
        // No rotation: axes unchanged.
        Orientation identity = Orientation.identity();
        assertEquals("x", identity.remapLogAxis("x"));
        assertEquals("y", identity.remapLogAxis("y"));
        assertEquals("z", identity.remapLogAxis("z"));

        // Pitch up swaps y and z, keeps x.
        Orientation pitchUp = Orientation.byIndex(Orientation.UP, 0);
        assertEquals("z", pitchUp.remapLogAxis("y"));
        assertEquals("y", pitchUp.remapLogAxis("z"));
        assertEquals("x", pitchUp.remapLogAxis("x"));
    }

    @Test
    void byIndexMatchesOf() {
        for (int i = 0; i < Orientation.COUNT; i++) {
            Orientation o = Orientation.byIndex(i);
            assertEquals(o, Orientation.of(o.getFacing(), o.getSpin()));
            assertEquals(i, o.getIndex());
        }
        // Negative spins wrap like a modulo.
        assertEquals(Orientation.byIndex(Orientation.NORTH, 1), Orientation.of(EnumFacing.NORTH, -3));
        assertNotEquals(Orientation.byIndex(Orientation.NORTH, 0), Orientation.byIndex(Orientation.NORTH, 1));
    }

    @Test
    void spinDirectionsDiffer() {
        // Same facing, different spins must rotate other axes differently.
        Orientation s0 = Orientation.byIndex(Orientation.UP, 0);
        Orientation s1 = Orientation.byIndex(Orientation.UP, 1);
        assertArrayEquals(new int[] {0, 1, 0}, s0.apply(0, 0, -1));
        assertArrayEquals(new int[] {0, 1, 0}, s1.apply(0, 0, -1)); // facing still up
        // But east must move under spin 1 while staying fixed under spin 0.
        assertArrayEquals(new int[] {1, 0, 0}, s0.apply(1, 0, 0));
        int[] spun = s1.apply(1, 0, 0);
        assertTrue(spun[0] == 0, "spin must move east off the x axis");
    }

    @Test
    void spinResidualComposesBackToFullOrientation() {
        // The block-model wrapper rotates the already facing-rotated quads by
        // the residual (facing-only-inverse composed with the full
        // orientation); applying the facing rotation first and the residual
        // after must reproduce the full orientation for all 24 cases.
        for (Orientation full : all()) {
            Orientation facingOnly = Orientation.of(full.getFacing(), 0);
            Orientation residual = facingOnly.invert().compose(full);
            assertEquals(matrixKey(full), matrixKey(facingOnly.compose(residual)),
                    "residual on top of facing rotation must equal full orientation " + full.getIndex());
        }
    }

    @Test
    void spinResidualFixesFacingAxisAndVanishesWithoutSpin() {
        for (Orientation full : all()) {
            Orientation facingOnly = Orientation.of(full.getFacing(), 0);
            Orientation residual = facingOnly.invert().compose(full);
            assertEquals(full.getFacing(), residual.rotateFacing(full.getFacing()),
                    "residual spins about the facing axis, so the facing must be fixed: " + full.getIndex());
            if (full.getSpin() == 0) {
                assertEquals(matrixKey(Orientation.identity()), matrixKey(residual),
                        "spin zero must leave no residual rotation: " + full.getIndex());
            }
        }
    }

    private static int[] directionOf(EnumFacing facing) {
        switch (facing) {
            case DOWN: return new int[] {0, -1, 0};
            case UP: return new int[] {0, 1, 0};
            case NORTH: return new int[] {0, 0, -1};
            case SOUTH: return new int[] {0, 0, 1};
            case WEST: return new int[] {-1, 0, 0};
            case EAST: return new int[] {1, 0, 0};
            default: throw new IllegalArgumentException();
        }
    }

    private static String matrixKey(Orientation o) {
        StringBuilder sb = new StringBuilder();
        int[][] m = o.getMatrix();
        for (int[] row : m) {
            for (int v : row) {
                sb.append(v).append(',');
            }
        }
        return sb.toString();
    }

    private static int[][] multiply(int[][] a, int[][] b) {
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
}
