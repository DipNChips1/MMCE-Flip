package github.dipnchips.mmceflip.common.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import hellfirepvp.modularmachinery.common.block.BlockController;

import github.dipnchips.mmceflip.util.ControllerOrientationAccess;

/**
 * Requests a controller spin change from the client's sneak-scroll
 * handler. The server validates reach and target before applying the
 * spin through the same path the stick click uses: reset the structure
 * for the new orientation, sync to clients, and answer with the action
 * bar message.
 *
 * The handler body runs on the server main thread; message handlers are
 * invoked on the network thread, which must not touch the world.
 */
public class PacketSpinController implements IMessage {

    private BlockPos pos;
    private int delta;

    public PacketSpinController() {
    }

    public PacketSpinController(BlockPos pos, int delta) {
        this.pos = pos;
        this.delta = delta;
    }

    @Override
    public void fromBytes(io.netty.buffer.ByteBuf buf) {
        this.pos = BlockPos.fromLong(buf.readLong());
        this.delta = buf.readByte();
    }

    @Override
    public void toBytes(io.netty.buffer.ByteBuf buf) {
        buf.writeLong(this.pos.toLong());
        buf.writeByte(this.delta);
    }

    public static class Handler implements IMessageHandler<PacketSpinController, IMessage> {

        private static final int[] SPIN_DEGREES = {0, 90, 180, 270};
        private static final double MAX_DISTANCE_SQ = 36.0D;

        @Override
        public IMessage onMessage(PacketSpinController message, MessageContext ctx) {
            NetHandlerPlayServer netHandler = ctx.getServerHandler();
            if (netHandler == null) {
                return null;
            }
            EntityPlayerMP player = netHandler.player;
            FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(() ->
                    applySpin(player, message));
            return null;
        }

        private static void applySpin(EntityPlayerMP player, PacketSpinController message) {
            if (player == null || player.isDead || player.world == null) {
                return;
            }
            World world = player.world;
            BlockPos pos = message.pos;
            if (world.getBlockState(pos).getBlock() instanceof BlockController
                    && player.getDistanceSq(pos) <= MAX_DISTANCE_SQ) {
                TileEntity te = world.getTileEntity(pos);
                if (te instanceof ControllerOrientationAccess) {
                    ControllerOrientationAccess controller = (ControllerOrientationAccess) te;
                    int spin = ((controller.MMCEFlip$getSpin() + message.delta) % 4 + 4) % 4;
                    controller.MMCEFlip$setSpin(spin);
                    controller.MMCEFlip$onOrientationChanged();
                    player.sendStatusMessage(new TextComponentTranslation("message.mmceflip.spin", SPIN_DEGREES[spin]), true);
                }
            }
        }
    }
}
