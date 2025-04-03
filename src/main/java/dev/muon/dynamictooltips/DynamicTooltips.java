package dev.muon.dynamictooltips;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynamicTooltips implements ModInitializer {

	public static final String MODID = "dynamictooltips";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

	@Override
	public void onInitialize() {
		LOGGER.info("Loading Dynamic Tooltips");
	}
}