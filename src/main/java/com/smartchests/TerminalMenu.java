package com.smartchests;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu behind the terminal screen. The item list is custom (sent as packets); the menu only carries the player's own
 * inventory slots so that every inventory change made by a transfer is synchronised to the client by vanilla.
 * Those slots are never shown or clicked by the screen.
 */
public class TerminalMenu extends AbstractContainerMenu {
	private final BlockPos pos; // null on the client

	/** Client constructor. */
	public TerminalMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, null);
	}

	public TerminalMenu(int containerId, Inventory inventory, BlockPos pos) {
		super(ModMenus.TERMINAL, containerId);
		this.pos = pos;
		for (int i = 0; i < 36; i++) {
			this.addSlot(new Slot(inventory, i, -10000, -10000));
		}
	}

	public BlockPos getPos() {
		return pos;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		if (pos == null) {
			return true;
		}
		return player.level().getBlockState(pos).is(ModBlocks.TERMINAL)
			&& player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
	}
}
