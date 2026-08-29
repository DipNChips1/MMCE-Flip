package github.dipnchips.mmceflip.client;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.block.BlockFactoryController;

import github.dipnchips.mmceflip.MMCEFlip;

/**
 * Writes complete blockstate files for every machine controller into the
 * game directory's resource override folder, the same
 * {@code resources/modularmachinery/blockstates/} location Modular Machinery
 * itself uses for its generated controller models.
 *
 * Modular Machinery's generated files only cover the four horizontal
 * facings, which leaves the up and down facings without model variants:
 * controllers cycled to a vertical facing would render as missing models,
 * and Forge's missing model has a crash of its own when tessellated with
 * ambient occlusion. These files add all six facings so vertical
 * controllers render correctly rotated instead.
 *
 * This runs after Modular Machinery's pre-initialization, so its generated
 * four-facing files are replaced. A file that already defines the vertical
 * facings is left untouched, so hand-crafted overrides that support them
 * (or earlier runs of this writer) are preserved.
 */
@SideOnly(Side.CLIENT)
public final class ControllerBlockstateWriter {

    private static final String MACHINE_MODEL = "modularmachinery:blockcontroller";
    private static final String FACTORY_MODEL = "modularmachinery:blockfactorycontroller";

    private ControllerBlockstateWriter() {
    }

    public static void writeAll() {
        File directory = new File("resources/modularmachinery/blockstates");
        if (!directory.exists() && !directory.mkdirs()) {
            MMCEFlip.logger.warn("Could not create controller blockstate directory {}", directory);
            return;
        }

        int written = 0;
        // The generic creative-tab controller blocks carry the same six-value
        // facing property, and the model loader bakes every possible state,
        // so they need vertical variants as much as the machine-specific ones.
        written += write(directory, "blockcontroller", MACHINE_MODEL);
        written += write(directory, "blockfactorycontroller", FACTORY_MODEL);
        for (BlockController controller : BlockController.MACHINE_CONTROLLERS.values()) {
            written += write(directory, controller.getRegistryName().getPath(), MACHINE_MODEL);
        }
        for (BlockController controller : BlockController.MOC_MACHINE_CONTROLLERS.values()) {
            written += write(directory, controller.getRegistryName().getPath(), MACHINE_MODEL);
        }
        for (BlockFactoryController controller : BlockFactoryController.FACTORY_CONTROLLERS.values()) {
            written += write(directory, controller.getRegistryName().getPath(), FACTORY_MODEL);
        }

        MMCEFlip.logger.info("Controller blockstates with vertical facing support: {} written", written);
    }

    private static int write(File directory, String path, String modelName) {
        File file = new File(directory, path + ".json");
        try {
            Path javaPath = file.toPath();
            if (file.exists()) {
                String existing = new String(Files.readAllBytes(javaPath), StandardCharsets.UTF_8);
                if (existing.contains("facing=up")) {
                    return 0;
                }
            }
            Files.write(javaPath, blockstateJson(modelName).getBytes(StandardCharsets.UTF_8));
            return 1;
        } catch (IOException e) {
            MMCEFlip.logger.warn("Could not write controller blockstate file {}", file, e);
            return 0;
        }
    }

    /**
     * Builds a blockstate json covering all six facings for both formed
     * states plus the item inventory variant. Horizontal facings use
     * y-rotations; up and down use x-rotations of the north variant.
     */
    private static String blockstateJson(String modelName) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("{\n  \"variants\": {\n");
        for (boolean formed : new boolean[] {false, true}) {
            String suffix = formed ? "true" : "false";
            variant(sb, modelName, "north", suffix, null, null);
            variant(sb, modelName, "east", suffix, "90", null);
            variant(sb, modelName, "south", suffix, "180", null);
            variant(sb, modelName, "west", suffix, "270", null);
            variant(sb, modelName, "up", suffix, null, "270");
            variant(sb, modelName, "down", suffix, null, "90");
        }
        sb.append("    \"inventory\": {\"model\": \"").append(modelName).append("\"}\n");
        sb.append("  }\n}\n");
        return sb.toString();
    }

    private static void variant(StringBuilder sb, String modelName, String facing, String formed, String yRot, String xRot) {
        sb.append("    \"facing=").append(facing).append(",formed=").append(formed).append("\": {\"model\": \"")
                .append(modelName).append('"');
        if (xRot != null) {
            sb.append(", \"x\": ").append(xRot);
        }
        if (yRot != null) {
            sb.append(", \"y\": ").append(yRot);
        }
        sb.append("},\n");
    }
}
