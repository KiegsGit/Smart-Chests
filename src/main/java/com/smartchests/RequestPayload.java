package com.smartchests;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** Client to server: what the player wants to do. The server re-validates everything. */
public record RequestPayload(int action, ItemStack stack, boolean confirm) implements CustomPacketPayload {
	public static final Type<RequestPayload> TYPE = new Type<>(SmartChestsMod.id("request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RequestPayload> CODEC = StreamCodec.ofMember(RequestPayload::write, RequestPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(action);
		ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
		buf.writeBoolean(confirm);
	}

	private static RequestPayload read(RegistryFriendlyByteBuf buf) {
		int action = buf.readVarInt();
		ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
		boolean confirm = buf.readBoolean();
		return new RequestPayload(action, stack, confirm);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
