package dev.findmeclientside;

import dev.findmeclientside.client.ClientEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(FindMeClientside.MOD_ID)
public final class FindMeClientside {
    public static final String MOD_ID = "findmeclientside";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FindMeClientside() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ClientEvents::register);
    }
}
