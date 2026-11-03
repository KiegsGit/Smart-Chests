package com.smartchests;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
	public static final MenuType<TerminalMenu> TERMINAL = Registry.register(
		BuiltInRegistries.MENU,
		ResourceKey.create(Registries.MENU, SmartChestsMod.id("terminal")),
		new MenuType<>(TerminalMenu::new, FeatureFlags.VANILLA_SET));

	public static void init() {
		// touching the class registers the menu
	}

	private ModMenus() {
	}
}
