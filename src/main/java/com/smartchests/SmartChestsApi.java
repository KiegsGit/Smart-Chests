package com.smartchests;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Hooks for other mods. */
public final class SmartChestsApi {
	@FunctionalInterface
	public interface AccessCheck {
		/** Return false to keep this container out of the player's terminal (claims, protection mods, ...). */
		boolean canAccess(ServerPlayer player, ServerLevel level, BlockPos containerPos);
	}

	/** Every listener must allow access. Called on the server for each container, on every scan and transfer. */
	public static final Event<AccessCheck> ACCESS_CHECK = EventFactory.createArrayBacked(AccessCheck.class,
		listeners -> (player, level, pos) -> {
			for (AccessCheck listener : listeners) {
				if (!listener.canAccess(player, level, pos)) {
					return false;
				}
			}
			return true;
		});

	private SmartChestsApi() {
	}
}
