package com.smartchests;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Server to client: the whole search index plus the result of the last action. */
public record DataPayload(List<Entry> entries, int containers, int unloadedChunks, int radius, int msg, int a, int b)
	implements CustomPacketPayload {

	/** One container holding an item. index = "Chest #index". */
	public record Loc(int index, BlockPos pos, int count, int kind) {
	}

	/** One distinct item (same item + same components) with its total and where it is. stack has count 1. */
	public record Entry(ItemStack stack, int total, List<Loc> locs) {
	}

	public static final Type<DataPayload> TYPE = new Type<>(SmartChestsMod.id("data"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DataPayload> CODEC = StreamCodec.ofMember(DataPayload::write, DataPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(containers);
		buf.writeVarInt(unloadedChunks);
		buf.writeVarInt(radius);
		buf.writeVarInt(msg);
		buf.writeVarInt(a);
		buf.writeVarInt(b);
		buf.writeVarInt(entries.size());
		for (Entry e : entries) {
			ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, e.stack());
			buf.writeVarInt(e.total());
			buf.writeVarInt(e.locs().size());
			for (Loc l : e.locs()) {
				buf.writeVarInt(l.index());
				buf.writeBlockPos(l.pos());
				buf.writeVarInt(l.count());
				buf.writeByte(l.kind());
			}
		}
	}

	private static DataPayload read(RegistryFriendlyByteBuf buf) {
		int containers = buf.readVarInt();
		int unloaded = buf.readVarInt();
		int radius = buf.readVarInt();
		int msg = buf.readVarInt();
		int a = buf.readVarInt();
		int b = buf.readVarInt();
		int n = buf.readVarInt();
		List<Entry> entries = new ArrayList<>(Math.min(n, 4096));
		for (int i = 0; i < n; i++) {
			ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
			int total = buf.readVarInt();
			int m = buf.readVarInt();
			List<Loc> locs = new ArrayList<>(Math.min(m, 512));
			for (int j = 0; j < m; j++) {
				locs.add(new Loc(buf.readVarInt(), buf.readBlockPos(), buf.readVarInt(), buf.readByte()));
			}
			entries.add(new Entry(stack, total, locs));
		}
		return new DataPayload(entries, containers, unloaded, radius, msg, a, b);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
