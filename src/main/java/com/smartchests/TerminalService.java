package com.smartchests;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.TrappedChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All the server-side logic: finding containers, building the search index and moving items.
 *
 * Safety rules every transfer follows:
 *  - the network is re-scanned for every action, nothing the client says about contents is trusted;
 *  - the amount is computed (what exists, what fits) BEFORE any inventory is modified;
 *  - items are removed with Container#removeItem and inserted with ItemStack#copyWithCount, so components
 *    (enchantments, names, damage, ...) travel with the stack;
 *  - anything that somehow does not fit goes back into storage, and as a last resort onto the ground. Never deleted.
 */
public final class TerminalService {
	public static final int ACT_REFRESH = 0;
	public static final int ACT_TAKE_STACK = 1;
	public static final int ACT_TAKE_ALL = 2;
	public static final int ACT_DEPOSIT_MATCH = 3;
	public static final int ACT_DEPOSIT_ALL = 4;

	public static final int KIND_CHEST = 0;
	public static final int KIND_TRAPPED = 1;
	public static final int KIND_BARREL = 2;
	public static final int KIND_SHULKER = 3;

	public static final int MSG_NONE = 0;
	public static final int MSG_TOOK = 1;      // a = moved, b = available before
	public static final int MSG_DEPOSITED = 2; // a = deposited, b = did not fit
	public static final int MSG_REFRESHED = 3;
	public static final int MSG_ERROR = 4;     // a = ERR_*

	public static final int ERR_NOTHING = 1;
	public static final int ERR_FULL = 2;
	public static final int ERR_CONFIRM = 3;
	public static final int ERR_INVALID = 4;
	public static final int ERR_NO_CONTAINERS = 5;

	private static final int MAX_ENTRIES = 4000;
	private static final int MAX_LOCS = 200;

	public record Result(int msg, int a, int b) {
		public static final Result NONE = new Result(MSG_NONE, 0, 0);
		public static final Result REFRESHED = new Result(MSG_REFRESHED, 0, 0);

		public static Result error(int code) {
			return new Result(MSG_ERROR, code, 0);
		}
	}

	public record Source(BlockPos pos, Container container, int kind) {
	}

	public record Scan(List<Source> sources, int unloadedChunks) {
	}

	// ------------------------------------------------------------------ scanning

	/** Finds every supported container around origin. Never loads a chunk. Only called on open / refresh / transfer. */
	public static Scan scan(ServerPlayer player, ServerLevel level, BlockPos origin) {
		int r = SmartChestsConfig.get().searchRadius;
		List<Source> found = new ArrayList<>();

		int unloaded = 0;
		for (int cx = (origin.getX() - r) >> 4; cx <= (origin.getX() + r) >> 4; cx++) {
			for (int cz = (origin.getZ() - r) >> 4; cz <= (origin.getZ() + r) >> 4; cz++) {
				if (!level.hasChunkAt(new BlockPos(cx << 4, origin.getY(), cz << 4))) {
					unloaded++;
				}
			}
		}

		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = origin.getX() - r; x <= origin.getX() + r; x++) {
			for (int z = origin.getZ() - r; z <= origin.getZ() + r; z++) {
				m.set(x, origin.getY(), z);
				if (!level.hasChunkAt(m)) {
					continue; // never force-load chunks
				}
				for (int y = origin.getY() - r; y <= origin.getY() + r; y++) {
					m.set(x, y, z);
					if (level.isOutsideBuildHeight(m)) {
						continue;
					}
					BlockState state = level.getBlockState(m);
					Block block = state.getBlock();
					if (block instanceof ChestBlock chest) {
						ChestType type = state.getValue(ChestBlock.TYPE);
						if (type == ChestType.RIGHT) {
							continue; // the LEFT half returns the combined container: count a double chest once
						}
						BlockPos pos = m.immutable();
						if (type != ChestType.SINGLE && !level.hasChunkAt(pos.relative(ChestBlock.getConnectedDirection(state)))) {
							continue; // other half is in an unloaded chunk
						}
						Container c = ChestBlock.getContainer(chest, state, level, pos, true);
						if (c != null && allowed(player, level, pos)) {
							found.add(new Source(pos, c, block instanceof TrappedChestBlock ? KIND_TRAPPED : KIND_CHEST));
						}
					} else if (block instanceof BarrelBlock || block instanceof ShulkerBoxBlock) {
						BlockPos pos = m.immutable();
						BlockEntity be = level.getBlockEntity(pos);
						if (be instanceof Container c && allowed(player, level, pos)) {
							found.add(new Source(pos, c, block instanceof BarrelBlock ? KIND_BARREL : KIND_SHULKER));
						}
					}
				}
			}
		}
		found.sort(Comparator
			.comparingDouble((Source s) -> s.pos().distSqr(origin))
			.thenComparingInt(s -> s.pos().getY())
			.thenComparingInt(s -> s.pos().getX())
			.thenComparingInt(s -> s.pos().getZ()));
		return new Scan(found, unloaded);
	}

	private static boolean allowed(ServerPlayer player, ServerLevel level, BlockPos pos) {
		return SmartChestsApi.ACCESS_CHECK.invoker().canAccess(player, level, pos);
	}

	// ------------------------------------------------------------------ index

	public static DataPayload build(ServerPlayer player, ServerLevel level, BlockPos origin, Result result) {
		Scan scan = scan(player, level, origin);
		return new DataPayload(aggregate(scan), scan.sources().size(), scan.unloadedChunks(),
			SmartChestsConfig.get().searchRadius, result.msg(), result.a(), result.b());
	}

	private static List<DataPayload.Entry> aggregate(Scan scan) {
		List<ItemStack> reps = new ArrayList<>();
		List<Integer> totals = new ArrayList<>();
		List<List<DataPayload.Loc>> locs = new ArrayList<>();

		for (int s = 0; s < scan.sources().size(); s++) {
			Source src = scan.sources().get(s);
			Map<Integer, Integer> local = new LinkedHashMap<>();
			Container c = src.container();
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack st = c.getItem(i);
				if (st.isEmpty()) {
					continue;
				}
				int k = -1;
				for (int j = 0; j < reps.size(); j++) {
					if (ItemStack.isSameItemSameComponents(reps.get(j), st)) {
						k = j;
						break;
					}
				}
				if (k < 0) {
					if (reps.size() >= MAX_ENTRIES) {
						continue;
					}
					k = reps.size();
					reps.add(st.copyWithCount(1));
					totals.add(0);
					locs.add(new ArrayList<>());
				}
				totals.set(k, totals.get(k) + st.getCount());
				local.merge(k, st.getCount(), Integer::sum);
			}
			for (Map.Entry<Integer, Integer> e : local.entrySet()) {
				List<DataPayload.Loc> list = locs.get(e.getKey());
				if (list.size() < MAX_LOCS) {
					list.add(new DataPayload.Loc(s + 1, src.pos(), e.getValue(), src.kind()));
				}
			}
		}
		List<DataPayload.Entry> out = new ArrayList<>(reps.size());
		for (int i = 0; i < reps.size(); i++) {
			out.add(new DataPayload.Entry(reps.get(i), totals.get(i), locs.get(i)));
		}
		return out;
	}

	// ------------------------------------------------------------------ withdraw

	/** Withdraws one stack (all = false) or everything available (all = true) of the given item. */
	public static Result take(ServerPlayer player, ServerLevel level, BlockPos origin, ItemStack rep, boolean all) {
		if (rep.isEmpty()) {
			return Result.error(ERR_INVALID);
		}
		Scan scan = scan(player, level, origin);

		int available = 0;
		for (Source src : scan.sources()) {
			Container c = src.container();
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack st = c.getItem(i);
				if (!st.isEmpty() && ItemStack.isSameItemSameComponents(st, rep)) {
					available += st.getCount();
				}
			}
		}
		if (available == 0) {
			return Result.error(ERR_NOTHING);
		}
		int capacity = capacity(player.getInventory(), rep);
		if (capacity == 0) {
			return Result.error(ERR_FULL);
		}
		int wanted = all ? available : Math.min(rep.getMaxStackSize(), available);
		int amount = Math.min(wanted, capacity);

		// 1. take it out of storage
		int need = amount;
		int got = 0;
		outer:
		for (Source src : scan.sources()) {
			Container c = src.container();
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack st = c.getItem(i);
				if (!st.isEmpty() && ItemStack.isSameItemSameComponents(st, rep)) {
					ItemStack removed = c.removeItem(i, Math.min(need, st.getCount()));
					got += removed.getCount();
					need -= removed.getCount();
					if (need <= 0) {
						break outer;
					}
				}
			}
		}

		// 2. give it to the player (capacity was checked, but anything left over still goes back, never lost)
		int moved = got;
		int left = got;
		Inventory inv = player.getInventory();
		while (left > 0) {
			int n = Math.min(left, rep.getMaxStackSize());
			ItemStack chunk = rep.copyWithCount(n);
			inv.add(chunk);
			int rem = chunk.getCount();
			left -= n;
			if (rem > 0) {
				moved -= rem;
				insert(scan.sources(), chunk);
				if (!chunk.isEmpty()) {
					level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), chunk));
				}
			}
		}
		return new Result(MSG_TOOK, moved, available);
	}

	/** How many items of rep fit into the main inventory + hotbar (slots 0-35). */
	private static int capacity(Inventory inv, ItemStack rep) {
		int cap = 0;
		int max = rep.getMaxStackSize();
		for (int i = 0; i < 36; i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty()) {
				cap += max;
			} else if (ItemStack.isSameItemSameComponents(s, rep)) {
				cap += Math.max(0, s.getMaxStackSize() - s.getCount());
			}
		}
		return cap;
	}

	// ------------------------------------------------------------------ deposit

	/**
	 * Deposits inventory + hotbar items. matching = true: only items equal to rep. matching = false: every item
	 * (needs confirm = true). Items that do not fit stay in the player's inventory.
	 */
	public static Result deposit(ServerPlayer player, ServerLevel level, BlockPos origin, ItemStack rep, boolean matching, boolean confirm) {
		if (matching && rep.isEmpty()) {
			return Result.error(ERR_INVALID);
		}
		if (!matching && !confirm) {
			return Result.error(ERR_CONFIRM);
		}
		Scan scan = scan(player, level, origin);
		if (scan.sources().isEmpty()) {
			return Result.error(ERR_NO_CONTAINERS);
		}
		Inventory inv = player.getInventory();
		int deposited = 0;
		int notFit = 0;
		for (int i = 0; i < 36; i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty() || (matching && !ItemStack.isSameItemSameComponents(s, rep))) {
				continue;
			}
			int before = s.getCount();
			insert(scan.sources(), s); // shrinks s by exactly what was stored
			deposited += before - s.getCount();
			notFit += s.getCount();
			if (s.isEmpty()) {
				inv.setItem(i, ItemStack.EMPTY);
			}
		}
		return new Result(MSG_DEPOSITED, deposited, notFit);
	}

	/** Inserts as much of stack as fits into the sources. Shrinks stack by exactly the amount stored. */
	private static void insert(List<Source> sources, ItemStack stack) {
		boolean shulkerItem = stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
		for (int pass = 0; pass < 2; pass++) { // pass 0: top up existing stacks, pass 1: empty slots
			for (Source src : sources) {
				if (shulkerItem && src.kind() == KIND_SHULKER) {
					continue; // a shulker box can never go inside a shulker box
				}
				Container c = src.container();
				for (int i = 0; i < c.getContainerSize(); i++) {
					if (stack.isEmpty()) {
						return;
					}
					if (!c.canPlaceItem(i, stack)) {
						continue;
					}
					ItemStack target = c.getItem(i);
					int limit = Math.min(c.getMaxStackSize(), stack.getMaxStackSize());
					if (pass == 0) {
						if (target.isEmpty() || !ItemStack.isSameItemSameComponents(target, stack) || target.getCount() >= limit) {
							continue;
						}
						int move = Math.min(limit - target.getCount(), stack.getCount());
						target.grow(move);
						stack.shrink(move);
						c.setChanged();
					} else {
						if (!target.isEmpty()) {
							continue;
						}
						int move = Math.min(limit, stack.getCount());
						c.setItem(i, stack.copyWithCount(move));
						stack.shrink(move);
						c.setChanged();
					}
				}
			}
		}
	}

	private TerminalService() {
	}
}
