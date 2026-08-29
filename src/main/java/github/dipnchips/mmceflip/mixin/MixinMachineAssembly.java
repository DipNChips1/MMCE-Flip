package github.dipnchips.mmceflip.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import hellfirepvp.modularmachinery.ModularMachinery;

import ink.ikx.mmce.common.assembly.MachineAssembly;

/**
 * Keeps the assembly stick's creative instant-build on the main server
 * thread.
 *
 * The auto-assembly network packet invokes creative assembly directly on the
 * Netty thread; mutating blocks off-thread races chunk synchronization, which
 * shows up as blocks that are collidable but invisible on clients until a
 * later chunk update. Re-dispatching through Modular Machinery's own task
 * executor (the same mechanism the stick's per-tick path uses) makes every
 * placement sync correctly.
 */
@Mixin(MachineAssembly.class)
public abstract class MixinMachineAssembly {

    @Inject(method = "assemblyCreative", at = @At("HEAD"), cancellable = true, remap = false)
    private void MMCEFlip$deferToServerThread(CallbackInfo ci) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || server.isCallingFromMinecraftThread()) {
            return;
        }

        MachineAssembly self = (MachineAssembly) (Object) this;
        ModularMachinery.EXECUTE_MANAGER.addSyncTask(self::assemblyCreative);
        ci.cancel();
    }
}
