package github.dipnchips.mmceflip.client;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.IRegistry;

import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import hellfirepvp.modularmachinery.common.block.BlockController;

import github.dipnchips.mmceflip.MMCEFlip;

/**
 * Wraps every machine controller block variant's baked model with
 * {@link OrientedControllerModel} after the model registry is built.
 *
 * The wrapper is a no-op for spin-zero controllers, so controllers without
 * a spin render byte-identically to vanilla.
 */
@Mod.EventBusSubscriber(value = Side.CLIENT, modid = MMCEFlip.MODID)
public final class ControllerModelWrapperHandler {

    private ControllerModelWrapperHandler() {
    }

    @SubscribeEvent
    public static void onModelBake(ModelBakeEvent event) {
        Set<Block> controllers = new HashSet<>();
        ImmutableSet<Block> allBlocks = ImmutableSet.copyOf(Block.REGISTRY);
        for (Block block : allBlocks) {
            if (block instanceof BlockController) {
                controllers.add(block);
            }
        }

        IRegistry<ModelResourceLocation, IBakedModel> registry = event.getModelRegistry();
        for (Block block : controllers) {
            for (IBlockState state : block.getBlockState().getValidStates()) {
                ModelResourceLocation location =
                        new ModelResourceLocation(block.getRegistryName(), variantString(state));
                IBakedModel model = registry.getObject(location);
                if (model == null || model instanceof OrientedControllerModel) {
                    continue;
                }
                registry.putObject(location, new OrientedControllerModel(model));
            }
        }
    }

    /**
     * Mirrors vanilla's {@code StateMapperBase} property string, so the
     * generated locations match what the model loader registered.
     */
    private static String variantString(IBlockState state) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<IProperty<?>, Comparable<?>> entry : state.getProperties().entrySet()) {
            if (builder.length() != 0) {
                builder.append(',');
            }
            builder.append(entry.getKey().getName());
            builder.append('=');
            builder.append(nameOf(entry.getKey(), entry.getValue()));
        }
        return builder.toString();
    }

    private static <T extends Comparable<T>> String nameOf(IProperty<T> property, Comparable<?> value) {
        return property.getName(property.getValueClass().cast(value));
    }
}
