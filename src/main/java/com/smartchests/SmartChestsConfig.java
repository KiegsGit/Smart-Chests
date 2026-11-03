package com.smartchests;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** config/smartchests.json. Values are sanity-checked on load, so a bad edit never breaks the game. */
public final class SmartChestsConfig {
	public static final int MAX_RADIUS = 32;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static SmartChestsConfig instance = new SmartChestsConfig();

	/** How many blocks around the terminal are searched for containers (1-32). */
	public int searchRadius = 16;

	public static SmartChestsConfig get() {
		return instance;
	}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("smartchests.json");
		SmartChestsConfig loaded = null;
		if (Files.exists(file)) {
			try {
				loaded = GSON.fromJson(Files.readString(file), SmartChestsConfig.class);
			} catch (IOException | RuntimeException e) {
				SmartChestsMod.LOGGER.warn("Could not read {}, using defaults: {}", file, e.toString());
			}
		}
		instance = loaded != null ? loaded : new SmartChestsConfig();
		instance.searchRadius = Math.max(1, Math.min(MAX_RADIUS, instance.searchRadius));
		try {
			Files.writeString(file, GSON.toJson(instance));
		} catch (IOException e) {
			SmartChestsMod.LOGGER.warn("Could not write {}: {}", file, e.toString());
		}
	}

	private SmartChestsConfig() {
	}
}
