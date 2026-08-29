package github.dipnchips.mmceflip.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.EnumFacing;

import net.minecraftforge.common.property.IExtendedBlockState;

import hellfirepvp.modularmachinery.common.block.BlockController;

import github.dipnchips.mmceflip.util.ControllerSpinProperty;
import github.dipnchips.mmceflip.util.Orientation;

/**
 * Baked model wrapper that renders a controller's block model at the full
 * orientation, including the spin that vanilla blockstate files cannot
 * express (their {@code x}/{@code y} rotations cannot compose into all 24
 * cube orientations).
 *
 * The blockstate files keep handling the facing rotation exactly as
 * before: for a spun controller the wrapper takes the already
 * facing-rotated quads and applies the residual rotation about the facing
 * axis, computed from the same {@link Orientation} group as the structure
 * rotation. Spin-zero controllers take the completely untouched delegate
 * path, so nothing changes for them.
 *
 * The spin reaches the model through the extended state as an unlisted
 * property (see {@code MixinBlockController#getExtendedState}); face
 * culling stays correct because quads are re-filed under the rotated
 * {@link EnumFacing} key the chunk renderer asks for.
 */
public class OrientedControllerModel implements IBakedModel {

    private static final int GENERAL_QUADS_INDEX = 6;

    private final IBakedModel delegate;

    /** Rotated quads by orientation index, each an array by facing ordinal plus general quads. */
    private final Map<Integer, List<BakedQuad>[]> spunQuads = new HashMap<>();

    public OrientedControllerModel(IBakedModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        Orientation orientation = orientationOf(state);
        if (orientation == null || orientation.getSpin() == 0) {
            return delegate.getQuads(state, side, rand);
        }
        List<BakedQuad>[] baked = spunQuads.get(orientation.getIndex());
        if (baked == null) {
            baked = bakeSpunQuads(state, orientation);
            spunQuads.put(orientation.getIndex(), baked);
        }
        return side == null ? baked[GENERAL_QUADS_INDEX] : baked[side.getIndex()];
    }

    private List<BakedQuad>[] bakeSpunQuads(IBlockState state, Orientation orientation) {
        Orientation facingOnly = Orientation.of(orientation.getFacing(), 0);
        Orientation residual = facingOnly.invert().compose(orientation);

        List<BakedQuad>[] out = new List[GENERAL_QUADS_INDEX + 1];
        for (int i = 0; i <= GENERAL_QUADS_INDEX; i++) {
            EnumFacing target = i == GENERAL_QUADS_INDEX ? null : EnumFacing.byIndex(i);
            EnumFacing source = target == null ? null : residual.invert().rotateFacing(target);
            out[i] = rotateQuads(delegate.getQuads(state, source, 0L), residual);
        }
        return out;
    }

    private static List<BakedQuad> rotateQuads(List<BakedQuad> quads, Orientation residual) {
        if (quads == null || quads.isEmpty()) {
            return quads;
        }
        int[][] m = residual.getMatrix();
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            out.add(rotateQuad(quad, m, residual));
        }
        return out;
    }

    private static BakedQuad rotateQuad(BakedQuad quad, int[][] m, Orientation residual) {
        int[] data = quad.getVertexData().clone();
        VertexFormat format = quad.getFormat();

        // Vanilla quads always carry four vertices and FaceBakery hardcodes
        // the data layout (28 ints, stride 7 for the item format), while
        // getIntegerSize() truncates unaligned byte totals. The stride is
        // therefore derived from the data itself, with getIntegerSize() as
        // a fallback for anything unusual.
        int stride = data.length / 4;
        if (stride <= 0 || data.length % 4 != 0) {
            stride = format.getIntegerSize();
            if (stride <= 0) {
                return quad;
            }
        }

        // Element offsets come from the format's own offset table, which is
        // what quad writers use (getSize() already includes the element
        // count, so accumulating it manually would double-count).
        int positionOffset = -1;
        int normalOffset = -1;
        for (int i = 0; i < format.getElementCount(); i++) {
            VertexFormatElement element = format.getElement(i);
            if (element.getUsage() == VertexFormatElement.EnumUsage.POSITION && element.getElementCount() >= 3) {
                positionOffset = format.getOffset(i) / 4;
            } else if (element.getUsage() == VertexFormatElement.EnumUsage.NORMAL && element.getElementCount() >= 3) {
                normalOffset = format.getOffset(i) / 4;
            }
        }

        // Never let a mismatched quad crash the render thread: an element
        // that does not fit its vertex slot is skipped instead.
        if (positionOffset < 0 || positionOffset + 2 >= stride) {
            positionOffset = -1;
        }
        if (normalOffset < 0 || normalOffset >= stride) {
            normalOffset = -1;
        }

        int vertexCount = data.length / stride;
        for (int v = 0; v < vertexCount; v++) {
            int base = v * stride;
            if (positionOffset >= 0) {
                // Vanilla blockstate rotations pivot around the block
                // center; the residual must too, or the cube orbits into
                // neighboring cells instead of spinning in place.
                float x = Float.intBitsToFloat(data[base + positionOffset]) - 0.5F;
                float y = Float.intBitsToFloat(data[base + positionOffset + 1]) - 0.5F;
                float z = Float.intBitsToFloat(data[base + positionOffset + 2]) - 0.5F;
                data[base + positionOffset] = Float.floatToIntBits(m[0][0] * x + m[0][1] * y + m[0][2] * z + 0.5F);
                data[base + positionOffset + 1] = Float.floatToIntBits(m[1][0] * x + m[1][1] * y + m[1][2] * z + 0.5F);
                data[base + positionOffset + 2] = Float.floatToIntBits(m[2][0] * x + m[2][1] * y + m[2][2] * z + 0.5F);
            }
            if (normalOffset >= 0) {
                // Normals are three bytes packed into the low bytes of a
                // single aligned int slot (x | y<<8 | z<<16), vanilla's
                // packing convention; rotate them inside that int.
                int packed = data[base + normalOffset];
                float nx = (byte) packed;
                float ny = (byte) (packed >>> 8);
                float nz = (byte) (packed >>> 16);
                byte rx = (byte) Math.round(m[0][0] * nx + m[0][1] * ny + m[0][2] * nz);
                byte ry = (byte) Math.round(m[1][0] * nx + m[1][1] * ny + m[1][2] * nz);
                byte rz = (byte) Math.round(m[2][0] * nx + m[2][1] * ny + m[2][2] * nz);
                data[base + normalOffset] = (packed & 0xFF000000)
                        | (rx & 0xFF)
                        | ((ry & 0xFF) << 8)
                        | ((rz & 0xFF) << 16);
            }
        }

        EnumFacing rotatedFace = quad.getFace() == null ? null : residual.rotateFacing(quad.getFace());
        return new BakedQuad(data, quad.getTintIndex(), rotatedFace, quad.getSprite(),
                quad.shouldApplyDiffuseLighting(), format);
    }

    @Nullable
    private static Orientation orientationOf(IBlockState state) {
        if (state == null || !(state.getBlock() instanceof BlockController)) {
            return null;
        }
        EnumFacing facing = state.getValue(BlockController.FACING);
        Integer spin = null;
        if (state instanceof IExtendedBlockState) {
            spin = ((IExtendedBlockState) state).getValue(ControllerSpinProperty.SPIN);
        }
        return Orientation.of(facing, spin == null ? 0 : spin);
    }

    @Override
    public boolean isAmbientOcclusion() {
        return delegate.isAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return delegate.isGui3d();
    }

    @Override
    public boolean isBuiltInRenderer() {
        return delegate.isBuiltInRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return delegate.getParticleTexture();
    }

    @Override
    public ItemCameraTransforms getItemCameraTransforms() {
        return delegate.getItemCameraTransforms();
    }

    @Override
    public ItemOverrideList getOverrides() {
        return delegate.getOverrides();
    }
}
