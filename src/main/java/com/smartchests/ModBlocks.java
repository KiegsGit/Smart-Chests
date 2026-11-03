package com.smartchests;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
	public static final String TERMINAL_NAME = "smart_storage_terminal";

	public static final SmartTerminalBlock TERMINAL = registerBlock();
	public static final BlockItem TERMINAL_ITEM = registerItem();

	private static SmartTerminalBlock registerBlock() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, SmartChestsMod.id(TERMINAL_NAME));
		BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
			.setId(key)
			.strength(3.0f, 6.0f)
			.sound(SoundType.METAL)
			.lightLevel(state -> 7);
		return Registry.register(BuiltInRegistries.BLOCK, key, new SmartTerminalBlock(props));
	}

	private static BlockItem registerItem() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SmartChestsMod.id(TERMINAL_NAME));
		Item.Properties props = new Item.Properties()
			.setId(key)
			.useBlockDescriptionPrefix()
			.rarity(Rarity.UNCOMMON);
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(TERMINAL, props));
	}

	private static final ResourceKey<CreativeModeTab> FUNCTIONAL_TAB =
		ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath("minecraft", "functional_blocks"));

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(FUNCTIONAL_TAB).register(output -> output.accept(TERMINAL_ITEM));
	}

	private ModBlocks() {
	}
}
