package com.smartchests;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmartChestsMod implements ModInitializer {
	public static final String MOD_ID = "smartchests";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		SmartChestsConfig.load();
		ModBlocks.init();
		ModMenus.init();
		TerminalNetworking.init();
		LOGGER.info("Smart Chests loaded (search radius {}).", SmartChestsConfig.get().searchRadius);
	}
}
