package github.dipnchips.mmceflip.client;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;

import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import hellfirepvp.modularmachinery.common.block.BlockController;

import github.dipnchips.mmceflip.MMCEFlip;
import github.dipnchips.mmceflip.common.network.PacketSpinController;
import github.dipnchips.mmceflip.util.ControllerOrientationAccess;

/**
 * Spins a machine controller with the scroll wheel while sneaking with an
 * empty hand: scrolling forward adds 90 degrees of spin, scrolling
 * backward removes 90 (so scrolling back from zero wraps to 270).
 *
 * The wheel event is canceled whenever it is handled, so the hotbar
 * selection stays put while a controller is targeted; it scrolls
 * normally again as soon as the crosshair leaves the controller or
 * something is held. The spin itself is applied by the server through
 * {@link PacketSpinController}, which validates reach and target.
 */
@Mod.EventBusSubscriber(value = Side.CLIENT, modid = MMCEFlip.MODID)
public final class ControllerScrollHandler {

    private ControllerScrollHandler() {
    }

    @SubscribeEvent
    public static void onMouseScroll(MouseEvent event) {
        if (event.getDwheel() == 0) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || mc.player == null || mc.world == null) {
            return;
        }
        if (!mc.player.isSneaking() || !mc.player.getHeldItemMainhand().isEmpty()) {
            return;
        }

        RayTraceResult hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        if (!(mc.world.getBlockState(pos).getBlock() instanceof BlockController)) {
            return;
        }
        TileEntity te = mc.world.getTileEntity(pos);
        if (!(te instanceof ControllerOrientationAccess)) {
            return;
        }

        int delta = event.getDwheel() > 0 ? 1 : -1;
        MMCEFlip.network.sendToServer(new PacketSpinController(pos, delta));
        event.setCanceled(true);
    }
}
