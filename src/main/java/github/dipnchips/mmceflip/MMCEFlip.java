package github.dipnchips.mmceflip;

import org.apache.logging.log4j.Logger;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import github.dipnchips.mmceflip.common.ControllerSpinHandler;
import github.dipnchips.mmceflip.common.network.PacketSpinController;

@Mod(
        modid = MMCEFlip.MODID,
        name = MMCEFlip.NAME,
        version = MMCEFlip.VERSION,
        dependencies = "required-after:modularmachinery@[2.3.2,);after:modularmachineryaddons;",
        acceptedMinecraftVersions = "[1.12]"
)
public class MMCEFlip {

    public static final String MODID = "mmceflip";
    public static final String NAME = "MMCE Flip";
    public static final String VERSION = "0.1.0";

    public static final String CLIENT_PROXY = "github.dipnchips.mmceflip.client.ClientProxy";
    public static final String COMMON_PROXY = "github.dipnchips.mmceflip.CommonProxy";

    public static Logger logger;

    public static SimpleNetworkWrapper network;

    @SidedProxy(clientSide = CLIENT_PROXY, serverSide = COMMON_PROXY)
    public static CommonProxy proxy;

    @Mod.Instance(MODID)
    public static MMCEFlip instance;

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        logger.info("{} {} loading", NAME, VERSION);
        network = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
        network.registerMessage(PacketSpinController.Handler.class, PacketSpinController.class, 0, Side.SERVER);
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void onInit(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new ControllerSpinHandler());
    }

    @Mod.EventHandler
    public void onPostInit(FMLPostInitializationEvent event) {
    }
}
