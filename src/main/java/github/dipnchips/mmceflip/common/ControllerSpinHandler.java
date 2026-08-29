package github.dipnchips.mmceflip.common;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import hellfirepvp.modularmachinery.common.tiles.base.TileMultiblockMachineController;

import github.dipnchips.mmceflip.util.ControllerOrientationAccess;

/**
 * Cycles the controller's spin on sneak-right-click while holding a stick.
 *
 * This cannot live in the block's {@code onBlockActivated}: vanilla skips
 * block interaction entirely while sneaking with an item in hand (items do
 * not bypass sneak use by default), so only this pre-suppression event can
 * observe the click. {@link PlayerInteractEvent.RightClickBlock} is the same
 * mechanism Modular Machinery's assembly stick itself uses.
 *
 * The spin is applied on the server only. The client handler must not
 * cancel the event: a client-side cancellation stops the vanilla
 * use-on-block packet from ever being sent, so the server would never see
 * the click. Instead the client lets the packet flow, the server event
 * applies the spin, cancels further server-side processing, and sends the
 * action-bar message back through the chat channel.
 */
public class ControllerSpinHandler {

    private static final int[] SPIN_DEGREES = {0, 90, 180, 270};

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getEntityPlayer().isSneaking() || event.getHand() != EnumHand.MAIN_HAND) {
            return;
        }

        ItemStack held = event.getItemStack();
        if (held.isEmpty() || held.getItem() != Items.STICK) {
            return;
        }

        World world = event.getWorld();
        BlockPos pos = event.getPos();
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof ControllerOrientationAccess) || !(te instanceof TileMultiblockMachineController)) {
            return;
        }

        if (world.isRemote) {
            // Do not cancel client-side: the vanilla use-on-block packet must
            // reach the server, where this event fires again and the spin is
            // applied.
            return;
        }

        event.setCanceled(true);

        ControllerOrientationAccess controller = (ControllerOrientationAccess) te;
        int spin = (controller.MMCEFlip$getSpin() + 1) % 4;
        controller.MMCEFlip$setSpin(spin);
        controller.MMCEFlip$onOrientationChanged();

        EntityPlayer player = event.getEntityPlayer();
        player.sendStatusMessage(new TextComponentTranslation("message.mmceflip.spin", SPIN_DEGREES[spin]), true);
    }
}
