package github.dipnchips.mmceflip.client;

import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import github.dipnchips.mmceflip.MMCEFlip;

/**
 * Runs the controller blockstate writer at the right point in the startup
 * sequence.
 *
 * Modular Machinery registers its machine controller blocks (and writes its
 * own generated blockstate files) on {@code RegistryEvent.Register<Block>},
 * which fires after all mods' pre-initialization. Subscribing to
 * {@code RegistryEvent.Register<Item>} at the lowest priority runs after
 * that, but still before model baking, so the six-facing files written here
 * are what the model loader sees.
 */
@Mod.EventBusSubscriber(value = Side.CLIENT, modid = MMCEFlip.MODID)
public final class ControllerBlockstateSubscriber {

    private ControllerBlockstateSubscriber() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRegisterItems(RegistryEvent.Register<Item> event) {
        ControllerBlockstateWriter.writeAll();
    }
}
