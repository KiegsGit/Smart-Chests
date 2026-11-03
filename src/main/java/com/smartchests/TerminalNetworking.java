package com.smartchests;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class TerminalNetworking {
	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(RequestPayload.TYPE, RequestPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DataPayload.TYPE, DataPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(RequestPayload.TYPE, (payload, context) -> handle(context.player(), payload));
	}

	/** Sends a fresh scan of the network to the player. */
	public static void sendData(ServerPlayer player, ServerLevel level, BlockPos origin, TerminalService.Result result) {
		ServerPlayNetworking.send(player, TerminalService.build(player, level, origin, result));
	}

	private static void handle(ServerPlayer player, RequestPayload payload) {
		// The player must really have a terminal open, close to it, and the block must still be there.
		if (!(player.containerMenu instanceof TerminalMenu menu) || menu.getPos() == null) {
			return;
		}
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		BlockPos pos = menu.getPos();
		if (!level.hasChunkAt(pos) || !level.getBlockState(pos).is(ModBlocks.TERMINAL)) {
			return;
		}
		if (player.distanceToSqr(Vec3.atCenterOf(pos)) > 64.0) {
			return;
		}
		TerminalService.Result result = switch (payload.action()) {
			case TerminalService.ACT_REFRESH -> TerminalService.Result.REFRESHED;
			case TerminalService.ACT_TAKE_STACK -> TerminalService.take(player, level, pos, payload.stack(), false);
			case TerminalService.ACT_TAKE_ALL -> TerminalService.take(player, level, pos, payload.stack(), true);
			case TerminalService.ACT_DEPOSIT_MATCH -> TerminalService.deposit(player, level, pos, payload.stack(), true, true);
			case TerminalService.ACT_DEPOSIT_ALL -> TerminalService.deposit(player, level, pos, ItemStackHolder.EMPTY, false, payload.confirm());
			default -> TerminalService.Result.error(TerminalService.ERR_INVALID);
		};
		sendData(player, level, pos, result);
	}

	private TerminalNetworking() {
	}
}
