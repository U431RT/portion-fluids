package com.portionfluids;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(PortionFluids.MODID)
public class PortionFluids {
    public static final String MODID = "portion_fluids";
    public static final Logger LOGGER = LogManager.getLogger();

    public PortionFluids() {
        LOGGER.info("定量倒液：允许陶壶的合成扣 100 mB，必须木桶或铁桶的合成扣 1000 mB，容器按剩余液体归还。");
    }
}
