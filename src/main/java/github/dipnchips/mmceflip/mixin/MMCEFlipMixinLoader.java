package github.dipnchips.mmceflip.mixin;

import java.util.Arrays;
import java.util.List;

import zone.rong.mixinbooter.ILateMixinLoader;

public class MMCEFlipMixinLoader implements ILateMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        return java.util.Arrays.asList("mixins.mmceflip.json", "mixins.mmceflip.addons.json");
    }
}
