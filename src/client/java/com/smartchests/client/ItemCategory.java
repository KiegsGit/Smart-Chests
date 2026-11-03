package com.smartchests.client;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/** Filter categories. Every item belongs to exactly one of the specific categories (ALL matches everything). */
enum ItemCategory {
	ALL("all"), BLOCKS("blocks"), TOOLS("tools"), FOOD("food"), MATERIALS("materials"), REDSTONE("redstone"), MISC("misc");

	final String key;

	ItemCategory(String key) {
		this.key = key;
	}

	private static final String[] REDSTONE_WORDS = {
		"redstone", "repeater", "comparator", "observer", "piston", "hopper", "dropper", "dispenser", "lever", "button",
		"pressure_plate", "tripwire", "target", "daylight_detector", "note_block", "lightning_rod", "sculk_sensor",
		"crafter", "detector_rail", "powered_rail", "activator_rail"
	};
	private static final Set<String> MATERIAL_IDS = Set.of(
		"diamond", "emerald", "coal", "charcoal", "lapis_lazuli", "quartz", "amethyst_shard", "netherite_scrap",
		"string", "leather", "feather", "bone", "bone_meal", "stick", "flint", "clay_ball", "brick", "nether_brick",
		"slime_ball", "gunpowder", "blaze_rod", "blaze_powder", "ender_pearl", "ender_eye", "ghast_tear",
		"prismarine_shard", "prismarine_crystals", "glowstone_dust", "sugar", "paper", "ink_sac", "glow_ink_sac",
		"phantom_membrane", "rabbit_hide", "rabbit_foot", "nautilus_shell", "echo_shard", "heart_of_the_sea",
		"dragon_breath", "shulker_shell", "honeycomb", "magma_cream", "turtle_scute", "armadillo_scute", "resin_clump",
		"copper_ingot", "iron_ingot", "gold_ingot", "netherite_ingot");

	static ItemCategory classify(ItemStack stack) {
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		for (String w : REDSTONE_WORDS) {
			if (id.contains(w)) {
				return REDSTONE;
			}
		}
		if (stack.has(DataComponents.FOOD)) {
			return FOOD;
		}
		if (stack.isDamageableItem()) {
			return TOOLS; // tools, weapons and armour all have durability
		}
		boolean block = stack.getItem() instanceof BlockItem;
		if (!block && (MATERIAL_IDS.contains(id) || id.startsWith("raw_") || id.endsWith("_ingot") || id.endsWith("_nugget")
			|| id.endsWith("_shard") || id.endsWith("_dust"))) {
			return MATERIALS;
		}
		return block ? BLOCKS : MISC;
	}
}
